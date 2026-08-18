/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 *   SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.fuseki;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.fuseki.servlets.ActionREST;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.graphrag.provider.ChatCompletionProvider;
import org.apache.jena.graphrag.provider.ProviderException;
import org.apache.jena.graphrag.retrieval.GraphRAGContext;
import org.apache.jena.graphrag.retrieval.GraphRAGContextService;
import org.apache.jena.graphrag.retrieval.GraphRAGSearch;
import org.apache.jena.graphrag.retrieval.GraphRAGSearchService;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.web.HttpSC;

/**
 * GraphRAG retrieval-augmented answer operation.
 * <p>
 * The optional {@code mode} parameter accepts {@code basic}, {@code local},
 * {@code global}, or {@code drift}. An explicit mode retrieves GraphRAG
 * context for the chat provider, or returns a deterministic abstention when no
 * context matches. A missing {@code mode} preserves established hybrid retrieval.
 */
public final class GraphRAGAnswerAction extends ActionREST {
    private static final Property MG_TEXT = DatasetFactory.create().getDefaultModel()
            .createProperty("http://ormynet.com/ns/msft-graphrag#text");
    private static final int PROMPT_OVERHEAD_TOKENS = 16;
    private static final String NO_CONTEXT_ANSWER = "Aucun contexte GraphRAG correspondant a la question.";

    private final DatasetGraph datasetGraph;
    private final GraphRAGConfiguration configuration;
    private final GraphRAGSearchService searchService;
    private final ChatCompletionProvider chatProvider;
    private final GraphRAGContextService contextService;

    GraphRAGAnswerAction(DatasetGraph datasetGraph, GraphRAGConfiguration configuration,
                         GraphRAGSearchService searchService, ChatCompletionProvider chatProvider) {
        this(datasetGraph, configuration, searchService, chatProvider, new GraphRAGContextService());
    }

    GraphRAGAnswerAction(DatasetGraph datasetGraph, GraphRAGConfiguration configuration,
                         GraphRAGSearchService searchService, ChatCompletionProvider chatProvider,
                         GraphRAGContextService contextService) {
        this.datasetGraph = Objects.requireNonNull(datasetGraph);
        this.configuration = Objects.requireNonNull(configuration);
        this.searchService = Objects.requireNonNull(searchService);
        this.chatProvider = Objects.requireNonNull(chatProvider);
        this.contextService = Objects.requireNonNull(contextService);
    }

    @Override public void validate(HttpAction action) {}
    @Override protected void doGet(HttpAction action) { answer(action); }
    @Override protected void doPost(HttpAction action) { answer(action); }

    private void answer(HttpAction action) {
        String question = action.getRequestParameter("q");
        if ( question == null || question.isBlank() ) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", "parametre 'q' requis");
            return;
        }
        String mode = action.getRequestParameter("mode");
        if ( mode != null && !GraphRAGContextService.supportsMode(mode) ) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", "mode invalide: " + mode);
            return;
        }
        int topK;
        try {
            topK = parseTopK(action);
        } catch (IllegalArgumentException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", ex.getMessage());
            return;
        }

        if ( GraphRAGContextService.DRIFT_MODE.equals(mode) ) {
            answerDrift(action, question, topK);
            return;
        }

        datasetGraph.begin(ReadWrite.READ);
        try {
            List<Citation> citations = mode == null
                    ? citations(searchService.search(datasetGraph, question, topK, configuration.hybridAlpha()))
                    : citations(contextService.retrieve(datasetGraph, mode, question, topK));
            if ( mode != null && citations.isEmpty() ) {
                writeAnswer(action, question, NO_CONTEXT_ANSWER, citations);
                return;
            }
            String answer = GraphRAGContextService.GLOBAL_MODE.equals(mode)
                    ? globalAnswer(question, citations)
                    : chatProvider.complete(question, boundedContextPassages(question, citations), configuration.systemPrompt());
            writeAnswer(action, question, answer, citations);
        } catch (ProviderException ex) {
            writeProviderError(action, ex);
        } finally {
            datasetGraph.end();
        }
    }

    private void answerDrift(HttpAction action, String question, int topK) {
        datasetGraph.begin(ReadWrite.READ);
        try {
                GraphRAGContext primer = contextService.retrieve(datasetGraph, GraphRAGContextService.DRIFT_MODE, question,
                                          Math.min(topK, configuration.driftCommunityTopK()));
            primer = GraphRAGContextService.boundDriftContext(primer, configuration.driftContextTokenBudget());
            DriftTraversal traversal = new DriftTraversal(citations(primer));
            if ( traversal.citations.isEmpty() ) {
                traversal.stopReason = "empty_primer";
                writeDriftAnswer(action, question, NO_CONTEXT_ANSWER, traversal);
                return;
            }

                String initialAnswer = chatProvider.complete(question, boundedContextPassages(question, traversal.citations,
                                                       configuration.driftContextTokenBudget()),
                    configuration.systemPrompt());
            for ( Citation communityReport : List.copyOf(traversal.citations) ) {
                if ( traversal.followUpCount() == configuration.driftMaxFollowUps() ) {
                    traversal.stopReason = "max_follow_ups_reached";
                    break;
                }
                String followUp = "community-report:" + communityReport.uri();
                traversal.pendingFollowUps.add(followUp);
                GraphRAGContext localContext = contextService.retrieve(datasetGraph, GraphRAGContextService.LOCAL_MODE,
                                            communityReport.text(), configuration.driftLocalTopK());
                for ( Citation localEvidence : citations(localContext) )
                    traversal.addCitation(localEvidence);
                traversal.pendingFollowUps.remove(followUp);
                traversal.completedFollowUps++;
            }
            if ( traversal.stopReason == null )
                traversal.stopReason = "community_primer_exhausted";
            List<String> synthesisEvidence = new ArrayList<>();
            synthesisEvidence.add(initialAnswer);
                    synthesisEvidence.addAll(boundedContextPassages(question, traversal.citations,
                                             configuration.driftContextTokenBudget()));
                    String answer = chatProvider.complete(question, boundedPassages(question, synthesisEvidence,
                                                    configuration.driftContextTokenBudget()),
                    configuration.systemPrompt());
            writeDriftAnswer(action, question, answer, traversal);
        } catch (IllegalArgumentException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", ex.getMessage());
        } catch (ProviderException ex) {
            writeProviderError(action, ex);
        } finally {
            datasetGraph.end();
        }
    }

        private static void writeProviderError(HttpAction action, ProviderException exception) {
        switch ( exception.category() ) {
        case AUTHENTICATION -> GraphRAGHttpJson.writeError(action, HttpSC.BAD_GATEWAY_502,
            "provider_authentication_failed", "authentification du fournisseur refusee");
        case TIMEOUT -> GraphRAGHttpJson.writeError(action, HttpSC.GATEWAY_TIMEOUT_504,
            "provider_timeout", "delai du fournisseur depasse");
        case UNAVAILABLE -> GraphRAGHttpJson.writeError(action, HttpSC.BAD_GATEWAY_502,
            "provider_unavailable", "provider indisponible");
        }
        }

    private String globalAnswer(String question, List<Citation> citations) {
        List<String> intermediateAnswers = new ArrayList<>();
        for ( Citation citation : citations ) {
            for ( String reportSegment : splitReport(citation.text()) ) {
                List<String> report = boundedPassages(question, List.of(reportSegment));
                if ( !report.isEmpty() )
                    intermediateAnswers.add(chatProvider.complete(question, report, configuration.systemPrompt()));
            }
        }
        return chatProvider.complete(question, boundedPassages(question, selectRatedPoints(question, intermediateAnswers)),
                configuration.systemPrompt());
    }

    private static List<String> splitReport(String report) {
        if ( report.isBlank() )
            return List.of();
        return List.of(report.split("(?<=[.!?])\\s+"));
    }

    private static List<String> selectRatedPoints(String question, List<String> points) {
        List<String> terms = List.of(question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"));
        List<String> ratedPoints = new ArrayList<>();
        for ( String point : points ) {
            String normalizedPoint = point.toLowerCase(Locale.ROOT);
            for ( String term : terms ) {
                if ( term.length() >= 3 && normalizedPoint.contains(term) ) {
                    ratedPoints.add(point);
                    break;
                }
            }
        }
        return ratedPoints.isEmpty() ? points : ratedPoints;
    }

    private int parseTopK(HttpAction action) {
        String value = action.getRequestParameter("topK");
        int topK;
        try {
            topK = value == null ? configuration.defaultTopK() : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("topK invalide: " + value);
        }
        if ( topK < 1 || topK > configuration.maxTopK() )
            throw new IllegalArgumentException("topK doit etre compris entre 1 et " + configuration.maxTopK());
        return topK;
    }

    private List<Citation> citations(GraphRAGSearch search) {
        Model model = DatasetFactory.wrap(datasetGraph).getDefaultModel();
        List<Citation> citations = new ArrayList<>();
        search.results().forEach(result -> {
            var statement = model.getResource(result.uri()).getProperty(MG_TEXT);
            if ( statement != null && statement.getObject().isLiteral() )
                citations.add(new Citation(result.uri(), statement.getString()));
        });
        return List.copyOf(citations);
    }

    private static List<Citation> citations(GraphRAGContext context) {
        return context.results().stream()
                .map(result -> new Citation(result.uri(), result.sourceText()))
                .toList();
    }

    private List<String> boundedContextPassages(String question, List<Citation> citations) {
        return boundedPassages(question, citations.stream().map(Citation::text).toList());
    }

    private List<String> boundedContextPassages(String question, List<Citation> citations, int tokenBudget) {
        return boundedPassages(question, citations.stream().map(Citation::text).toList(), tokenBudget);
    }

    private List<String> boundedPassages(String question, List<String> candidates) {
        return boundedPassages(question, candidates, 4096);
    }

    private List<String> boundedPassages(String question, List<String> candidates, int tokenBudget) {
        int remainingBudget = Math.max(1, tokenBudget - estimateTokens(question) - PROMPT_OVERHEAD_TOKENS);
        List<String> passages = new ArrayList<>();
        for ( String candidate : candidates ) {
            if ( remainingBudget <= 0 )
                break;
            String boundedText = truncateToBudget(candidate, remainingBudget);
            int used = estimateTokens(boundedText);
            if ( used <= 0 )
                continue;
            passages.add(boundedText);
            remainingBudget -= used;
        }
        return List.copyOf(passages);
    }

    private static String truncateToBudget(String text, int tokenBudget) {
        String normalized = text == null ? "" : text.strip();
        if ( normalized.isEmpty() )
            return "";
        String[] words = normalized.split("\\s+");
        if ( words.length <= tokenBudget )
            return normalized;
        return String.join(" ", java.util.Arrays.copyOf(words, tokenBudget));
    }

    private static int estimateTokens(String text) {
        String normalized = text == null ? "" : text.strip();
        if ( normalized.isEmpty() )
            return 0;
        return normalized.split("\\s+").length;
    }

    private static void writeAnswer(HttpAction action, String question, String answer, List<Citation> citations) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject().pair("query", question).pair("answer", answer).key("citations").startArray();
        citations.forEach(citation -> builder.startObject().pair("uri", citation.uri()).pair("text", citation.text()).finishObject());
        builder.finishArray().finishObject();
        GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.OK_200);
    }

    private static void writeDriftAnswer(HttpAction action, String question, String answer, DriftTraversal traversal) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject().pair("query", question).pair("answer", answer)
                .pair("reasonStop", traversal.stopReason).pair("followUpCount", traversal.followUpCount())
                .key("citations").startArray();
        traversal.citations.forEach(citation -> builder.startObject().pair("uri", citation.uri())
                .pair("text", citation.text()).finishObject());
        builder.finishArray().finishObject();
        GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.OK_200);
    }

    private record Citation(String uri, String text) {}

    private static final class DriftTraversal {
        private final List<Citation> citations;
        private final Set<String> visitedResources = new HashSet<>();
        private final List<String> pendingFollowUps = new ArrayList<>();
        private int completedFollowUps;
        private String stopReason;

        private DriftTraversal(List<Citation> primer) {
            this.citations = new ArrayList<>();
            primer.forEach(this::addCitation);
        }

        private void addCitation(Citation citation) {
            if ( visitedResources.add(citation.uri()) )
                citations.add(citation);
        }

        private int followUpCount() {
            return completedFollowUps;
        }
    }

    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}