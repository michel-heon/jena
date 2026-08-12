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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.graphrag.provider.ProviderException;
import org.apache.jena.graphrag.retrieval.CommunityReportVectorSearchService;
import org.apache.jena.graphrag.retrieval.GraphRAGContextService;
import org.apache.jena.graphrag.retrieval.GraphRAGSearchService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGAnswerEndpoint {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String REFERENCE_CORPUS = "/org/apache/jena/graphrag/graphrag-retrieval-reference.ttl";

    @Test
    public void enabledModule_returnsDeterministicMockAnswer() throws Exception {
        FusekiServer server = server(true);
        try {
            HttpResponse<String> response = get(server, "?q=What%20is%20GraphRAG%3F");
            JsonObject body = JSON.parse(response.body());
            assertEquals(200, response.statusCode());
            assertEquals("What is GraphRAG?", body.get("query").getAsString().value());
            assertEquals("[mock] No context available for: What is GraphRAG?", body.get("answer").getAsString().value());
            assertTrue(body.get("citations").getAsArray().isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    public void enabledModule_explicitModesWithoutContextReturnDeterministicAbstention() throws Exception {
        FusekiServer server = server(true);
        try {
            for ( String mode : new String[] { "basic", "local", "global" } ) {
                HttpResponse<String> response = get(server, "?q=What%20is%20GraphRAG%3F&mode=" + mode);
                JsonObject body = JSON.parse(response.body());

                assertEquals(200, response.statusCode());
                assertEquals("Aucun contexte GraphRAG correspondant a la question.", body.get("answer").getAsString().value());
                assertTrue(body.get("citations").getAsArray().isEmpty());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void driftMode_synthesizesCommunityPrimerAndDeduplicatedLocalEvidence() throws Exception {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.WRITE);
        try {
            Model model = dataset.getDefaultModel();
            model.createResource("urn:community:drift")
                    .addProperty(RDF.type, GRAG.Community)
                    .addProperty(GRAG.summary, "Scrooge community report");
            Resource scrooge = model.createResource("urn:entity:scrooge")
                    .addProperty(RDF.type, GRAG.Entity).addProperty(GRAG.name, "Scrooge");
            Resource marley = model.createResource("urn:entity:marley")
                    .addProperty(RDF.type, GRAG.Entity).addProperty(GRAG.name, "Marley");
            model.createResource("urn:relationship:scrooge-marley")
                    .addProperty(RDF.type, GRAG.Relationship).addProperty(GRAG.source, scrooge)
                    .addProperty(GRAG.target, marley).addProperty(GRAG.description, "Scrooge knew Marley.");
            dataset.commit();
        } finally {
            dataset.end();
        }
        try (LuceneVectorIndex communityIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2,
                VectorSimilarityFunction.EUCLIDEAN)) {
            communityIndex.index("urn:community:drift", new float[] { 1.0f, 0.0f });
            CommunityReportVectorSearchService communitySearch = new CommunityReportVectorSearchService(
                    communityIndex, (text, dimension) -> new float[] { 1.0f, 0.0f }, 2);
            GraphRAGContextService contextService = new GraphRAGContextService(null, communitySearch);
            List<List<String>> providerCalls = new ArrayList<>();
            GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                    (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                            GraphRAGSearchAction.defaultSearchService(), (question, passages) -> {
                                providerCalls.add(List.copyOf(passages));
                                return "answer-" + providerCalls.size();
                            }, contextService));
            FusekiServer server = server(dataset, true, module);
            try {
                JsonObject body = JSON.parse(get(server, "?q=Scrooge&mode=drift&topK=1").body());

                assertEquals("answer-2", body.get("answer").getAsString().value());
                assertEquals("community_primer_exhausted", body.get("reasonStop").getAsString().value());
                assertEquals(1, body.get("followUpCount").getAsNumber().value().intValue());
                assertEquals(2, providerCalls.size());
                assertEquals("urn:community:drift", body.get("citations").getAsArray().get(0).getAsObject()
                        .get("uri").getAsString().value());
                assertEquals("urn:relationship:scrooge-marley", body.get("citations").getAsArray().get(1).getAsObject()
                        .get("uri").getAsString().value());
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void enabledModule_returnsRetrievedCitationToChatProvider() throws Exception {
        Dataset dataset = GraphRAGTextDatasetFactory.createChunkTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.getDefaultModel().createResource("urn:chunk:graphrag")
                    .addProperty(RDF.type, GRAG.Chunk)
                    .addProperty(GRAG.text, "GraphRAG combines graph structure and retrieval.");
            dataset.commit();
        } finally {
            dataset.end();
        }
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2,
                VectorSimilarityFunction.EUCLIDEAN)) {
            GraphRAGSearchService service = new GraphRAGSearchService(vectorIndex, (text, dimension) -> new float[dimension], 2);
            GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                    (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration, service,
                            (question, passages) -> "passages=" + passages.size() + ":" + passages.getFirst()));
            FusekiServer server = server(dataset, true, module);
            try {
                JsonObject body = JSON.parse(get(server, "?q=GraphRAG&topK=1").body());
                assertEquals("passages=1:GraphRAG combines graph structure and retrieval.",
                        body.get("answer").getAsString().value());
                assertEquals("urn:chunk:graphrag", body.get("citations").getAsArray().get(0).getAsObject()
                        .get("uri").getAsString().value());
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void explicitMode_usesContextPassagesAndMatchingCitations() throws Exception {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.WRITE);
        try {
            Model model = dataset.getDefaultModel();
            model.createResource("urn:chunk:basic")
                    .addProperty(RDF.type, GRAG.Chunk)
                    .addProperty(GRAG.text, "Basic passage");
            Resource source = model.createResource("urn:entity:graphrag")
                    .addProperty(RDF.type, GRAG.Entity)
                    .addProperty(GRAG.name, "GraphRAG");
            Resource target = model.createResource("urn:entity:jena")
                    .addProperty(RDF.type, GRAG.Entity)
                    .addProperty(GRAG.name, "Jena");
            model.createResource("urn:relationship:graphrag-jena")
                    .addProperty(RDF.type, GRAG.Relationship)
                    .addProperty(GRAG.source, source)
                    .addProperty(GRAG.target, target)
                    .addProperty(GRAG.description, "Local passage");
            model.createResource("urn:community:global")
                    .addProperty(RDF.type, GRAG.Community)
                    .addProperty(GRAG.summary, "Global passage");
            dataset.commit();
        } finally {
            dataset.end();
        }
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(), (question, passages) -> String.join("|", passages)));
        FusekiServer server = server(dataset, true, module);
        try {
            assertModeAnswer(server, "basic", "Basic", "urn:chunk:basic", "Basic passage");
            assertModeAnswer(server, "local", "GraphRAG", "urn:relationship:graphrag-jena", "Local passage");
            assertModeAnswer(server, "global", "Global", "urn:community:global", "Global passage");
        } finally {
            server.stop();
        }
    }

    @Test
    public void referenceCorpus_explicitModesPassOnlyCitedContextToProvider() throws Exception {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.WRITE);
        try (InputStream in = TestGraphRAGAnswerEndpoint.class.getResourceAsStream(REFERENCE_CORPUS)) {
            dataset.getDefaultModel().read(in, null, "TURTLE");
            dataset.commit();
        } finally {
            dataset.end();
        }
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(), (question, passages) -> String.join("|", passages)));
        FusekiServer server = server(dataset, true, module);
        try {
            assertModeAnswer(server, "basic", "Semantic", "urn:graphrag:reference:chunk-retrieval",
                    "Semantic retrieval ranks relevant GraphRAG chunks for a question.");
            assertModeAnswer(server, "local", "GraphRAG", "urn:graphrag:reference:relationship-graphrag-jena",
                    "GraphRAG uses Jena RDF resources to keep retrieval citations traceable.");
            assertModeAnswer(server, "global", "platform", "urn:graphrag:reference:community-platform",
                    "The platform overview joins retrieval and governance for GraphRAG.");
        } finally {
            server.stop();
        }
    }

    @Test
    public void globalMode_mapsEachCommunityReportBeforeReducingAnswer() throws Exception {
        Dataset dataset = referenceDataset();
        List<List<String>> providerCalls = new ArrayList<>();
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(), (question, passages) -> {
                            providerCalls.add(List.copyOf(passages));
                            return "intermediate-" + providerCalls.size();
                        }));
        FusekiServer server = server(dataset, true, module);
        try {
            JsonObject body = JSON.parse(get(server, "?q=GraphRAG&mode=global&topK=3").body());

            assertEquals("intermediate-4", body.get("answer").getAsString().value());
            assertEquals(List.of(
                    List.of("Jena governance keeps GraphRAG citations traceable."),
                    List.of("The platform overview joins retrieval and governance for GraphRAG."),
                    List.of("Semantic retrieval makes GraphRAG chunks relevant to a question."),
                    List.of("intermediate-1", "intermediate-2", "intermediate-3")), providerCalls);
            assertEquals(3, body.get("citations").getAsArray().size());
        } finally {
            server.stop();
        }
    }

    @Test
    public void globalMode_reducesOnlyQuestionRatedIntermediatePoints() throws Exception {
        Dataset dataset = referenceDataset();
        List<List<String>> providerCalls = new ArrayList<>();
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(), (question, passages) -> {
                            providerCalls.add(List.copyOf(passages));
                            if ( providerCalls.size() <= 3 )
                                return passages.getFirst().contains("Semantic retrieval makes")
                                        ? "GraphRAG retrieval point"
                                        : "unrelated point";
                            return String.join("|", passages);
                        }));
        FusekiServer server = server(dataset, true, module);
        try {
            JsonObject body = JSON.parse(get(server, "?q=GraphRAG&mode=global&topK=3").body());

            assertEquals("GraphRAG retrieval point", body.get("answer").getAsString().value());
            assertEquals(4, providerCalls.size());
            assertEquals(List.of("GraphRAG retrieval point"), providerCalls.get(3));
        } finally {
            server.stop();
        }
    }

    @Test
    public void missingQuery_returnsStructuredBadRequest() throws Exception {
        FusekiServer server = server(true);
        try {
            HttpResponse<String> response = get(server, "");
            JsonObject error = JSON.parse(response.body()).get("error").getAsObject();
            assertEquals(400, response.statusCode());
            assertEquals("invalid_request", error.get("code").getAsString().value());
        } finally {
            server.stop();
        }
    }

    @Test
    public void providerFailure_returnsSanitizedUnavailableError() throws Exception {
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(),
                        (question, passages) -> { throw new ProviderException("apiKey=not-for-response"); }));
        FusekiServer server = server(DatasetFactory.createTxnMem(), true, module);
        try {
            HttpResponse<String> response = get(server, "?q=GraphRAG");
            JsonObject error = JSON.parse(response.body()).get("error").getAsObject();
            assertEquals(502, response.statusCode());
            assertEquals("provider_unavailable", error.get("code").getAsString().value());
            assertEquals("provider indisponible", error.get("message").getAsString().value());
            assertFalse(response.body().contains("not-for-response"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void providerAuthenticationFailure_returnsSanitizedGatewayError() throws Exception {
        assertProviderFailure(ProviderException.Category.AUTHENTICATION, 502, "provider_authentication_failed");
    }

    @Test
    public void providerTimeout_returnsSanitizedGatewayTimeout() throws Exception {
        assertProviderFailure(ProviderException.Category.TIMEOUT, 504, "provider_timeout");
    }

    @Test
    public void disabledModule_doesNotExposeAnswerEndpoint() throws Exception {
        FusekiServer server = server(false);
        try {
            assertEquals(404, get(server, "?q=test").statusCode());
        } finally {
            server.stop();
        }
    }

    private static FusekiServer server(boolean enabled) {
        return server(DatasetFactory.createTxnMem(), enabled, new GraphRAGModule());
    }

    private static Dataset referenceDataset() {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.WRITE);
        try (InputStream in = TestGraphRAGAnswerEndpoint.class.getResourceAsStream(REFERENCE_CORPUS)) {
            dataset.getDefaultModel().read(in, null, "TURTLE");
            dataset.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static FusekiServer server(Dataset dataset, boolean enabled, GraphRAGModule module) {
        Model config = ModelFactory.createDefaultModel();
        if ( enabled )
            config.createResource("urn:graphrag:test")
                    .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        return FusekiServer.create().port(0).add("/ds", dataset).parseConfig(config)
            .fusekiModules(FusekiModules.create(module)).build().start();
    }

    private static HttpResponse<String> get(FusekiServer server, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + server.getPort() + "/ds/graphrag/answer" + query)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertModeAnswer(FusekiServer server, String mode, String question,
            String expectedUri, String expectedPassage) throws Exception {
        JsonObject body = JSON.parse(get(server, "?q=" + question + "&mode=" + mode + "&topK=1").body());
        assertEquals(expectedPassage, body.get("answer").getAsString().value());
        JsonObject citation = body.get("citations").getAsArray().getFirst().getAsObject();
        assertEquals(expectedUri, citation.get("uri").getAsString().value());
        assertEquals(expectedPassage, citation.get("text").getAsString().value());
    }

    private static void assertProviderFailure(ProviderException.Category category, int expectedStatus, String expectedCode)
            throws Exception {
        GraphRAGModule module = new GraphRAGModule(GraphRAGSearchAction::new,
                (datasetGraph, configuration) -> new GraphRAGAnswerAction(datasetGraph, configuration,
                        GraphRAGSearchAction.defaultSearchService(),
                        (question, passages) -> { throw new ProviderException(category, "apiKey=not-for-response"); }));
        FusekiServer server = server(DatasetFactory.createTxnMem(), true, module);
        try {
            HttpResponse<String> response = get(server, "?q=GraphRAG");
            JsonObject error = JSON.parse(response.body()).get("error").getAsObject();
            assertEquals(expectedStatus, response.statusCode());
            assertEquals(expectedCode, error.get("code").getAsString().value());
            assertFalse(response.body().contains("not-for-response"));
        } finally {
            server.stop();
        }
    }
}