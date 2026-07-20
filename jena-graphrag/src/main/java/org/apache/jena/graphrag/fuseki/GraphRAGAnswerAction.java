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
import java.util.List;
import java.util.Objects;

import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.fuseki.servlets.ActionREST;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.graphrag.provider.ChatCompletionProvider;
import org.apache.jena.graphrag.provider.ProviderException;
import org.apache.jena.graphrag.retrieval.GraphRAGSearch;
import org.apache.jena.graphrag.retrieval.GraphRAGSearchService;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.web.HttpSC;

/** GraphRAG retrieval-augmented answer operation. */
public final class GraphRAGAnswerAction extends ActionREST {
    private final DatasetGraph datasetGraph;
    private final GraphRAGConfiguration configuration;
    private final GraphRAGSearchService searchService;
    private final ChatCompletionProvider chatProvider;

    GraphRAGAnswerAction(DatasetGraph datasetGraph, GraphRAGConfiguration configuration,
                         GraphRAGSearchService searchService, ChatCompletionProvider chatProvider) {
        this.datasetGraph = Objects.requireNonNull(datasetGraph);
        this.configuration = Objects.requireNonNull(configuration);
        this.searchService = Objects.requireNonNull(searchService);
        this.chatProvider = Objects.requireNonNull(chatProvider);
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
        int topK;
        try {
            topK = parseTopK(action);
        } catch (IllegalArgumentException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", ex.getMessage());
            return;
        }

        datasetGraph.begin(ReadWrite.READ);
        try {
            GraphRAGSearch search = searchService.search(datasetGraph, question, topK, configuration.hybridAlpha());
            List<Citation> citations = citations(search);
            String answer = chatProvider.complete(question, citations.stream().map(Citation::text).toList());
            writeAnswer(action, question, answer, citations);
        } catch (ProviderException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_GATEWAY_502, "provider_error", "provider indisponible");
        } finally {
            datasetGraph.end();
        }
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
            var statement = model.getResource(result.uri()).getProperty(GRAG.text);
            if ( statement != null && statement.getObject().isLiteral() )
                citations.add(new Citation(result.uri(), statement.getString()));
        });
        return List.copyOf(citations);
    }

    private static void writeAnswer(HttpAction action, String question, String answer, List<Citation> citations) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject().pair("query", question).pair("answer", answer).key("citations").startArray();
        citations.forEach(citation -> builder.startObject().pair("uri", citation.uri()).pair("text", citation.text()).finishObject());
        builder.finishArray().finishObject();
        GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.OK_200);
    }

    private record Citation(String uri, String text) {}

    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}