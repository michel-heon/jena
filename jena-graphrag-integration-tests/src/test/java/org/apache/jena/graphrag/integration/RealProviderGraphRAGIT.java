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

package org.apache.jena.graphrag.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.fuseki.GraphRAGModule;
import org.apache.jena.graphrag.index.GraphRAGAssemblerVocab;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executes only through the graphrag-real-providers Maven profile. */
public class RealProviderGraphRAGIT {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String DATASET = "graphrag";
    private static final String QUERY = "What does Apache Jena GraphRAG index?";

    @TempDir
    Path tempDir;

    @Test
    public void realProviders_indexRetrieveAndAnswerWithCitation() throws Exception {
        Map<String, String> environment = System.getenv();
        ExternalProviderPrerequisites.requireRealProviderEnvironment(environment);
        assertRuntimeConfiguration(environment);
        FusekiServer server = server(environment);
        try {
            JsonObject accepted = JSON.parse(post(server, "index", """
                    {"title":"GraphRAG integration corpus","content":"Apache Jena GraphRAG indexes cited knowledge.","sourceUri":"urn:graphrag:real-provider"}
                    """).body());
            JsonObject task = awaitTask(server, accepted.get("taskId").getAsString().value());
                assertEquals("done", task.get("status").getAsString().value(), task.toString());

                HttpResponse<String> searchResponse = get(server, "search?q=" + queryParameter(QUERY));
                assertEquals(200, searchResponse.statusCode());
                JsonObject search = JSON.parse(searchResponse.body());
                assertTrue(search.get("results").getAsArray().size() >= 1);
                assertTrue(search.get("results").getAsArray().getFirst().getAsObject().get("uri").getAsString().value()
                    .startsWith("urn:graphrag:real-provider#chunk-"));

                HttpResponse<String> basicContextResponse = get(server,
                    "context?q=" + queryParameter(QUERY) + "&mode=basic&topK=1");
                assertEquals(200, basicContextResponse.statusCode());
                JsonObject basicContext = JSON.parse(basicContextResponse.body());
                assertEquals(1, basicContext.get("results").getAsArray().size());
                assertEquals("chunk", basicContext.get("results").getAsArray().getFirst().getAsObject()
                    .get("type").getAsString().value());
                assertTrue(basicContext.get("results").getAsArray().getFirst().getAsObject()
                    .get("uri").getAsString().value().startsWith("urn:graphrag:real-provider#chunk-"));

                HttpResponse<String> basicAnswerResponse = get(server,
                    "answer?q=" + queryParameter(QUERY) + "&mode=basic&topK=1");
                assertEquals(200, basicAnswerResponse.statusCode());
                JsonObject basicAnswer = JSON.parse(basicAnswerResponse.body());
                assertFalse(basicAnswer.get("answer").getAsString().value().isBlank());
                assertEquals(1, basicAnswer.get("citations").getAsArray().size());
                assertEquals(basicContext.get("results").getAsArray().getFirst().getAsObject()
                    .get("uri").getAsString().value(), basicAnswer.get("citations").getAsArray().getFirst()
                    .getAsObject().get("uri").getAsString().value());

                HttpResponse<String> answerResponse = get(server, "answer?q=" + queryParameter(QUERY));
                assertEquals(200, answerResponse.statusCode());
                JsonObject answer = JSON.parse(answerResponse.body());
            assertFalse(answer.get("answer").getAsString().value().isBlank());
            assertTrue(answer.get("citations").getAsArray().size() >= 1);
            assertTrue(answer.get("citations").getAsArray().getFirst().getAsObject().get("uri").getAsString().value()
                    .startsWith("urn:graphrag:real-provider#chunk-"));

            String driftQuery = "Which GraphRAG community publishes citations?";
            HttpResponse<String> driftContextResponse = get(server,
                    "context?q=" + queryParameter(driftQuery) + "&mode=drift&topK=1");
            assertEquals(200, driftContextResponse.statusCode(), driftContextResponse.body());
            JsonObject driftContext = JSON.parse(driftContextResponse.body());
            assertEquals("drift", driftContext.get("mode").getAsString().value());
            assertEquals(1, driftContext.get("results").getAsArray().size());
            assertEquals("urn:graphrag:real-provider:community", driftContext.get("results").getAsArray().getFirst()
                    .getAsObject().get("communityUri").getAsString().value());

            HttpResponse<String> driftAnswerResponse = get(server,
                    "answer?q=" + queryParameter(driftQuery) + "&mode=drift&topK=1");
            assertEquals(200, driftAnswerResponse.statusCode());
            JsonObject driftAnswer = JSON.parse(driftAnswerResponse.body());
            assertFalse(driftAnswer.get("answer").getAsString().value().isBlank());
            assertTrue(driftAnswer.hasKey("reasonStop"));
            assertTrue(driftAnswer.hasKey("followUpCount"));
            assertEquals("urn:graphrag:real-provider:community", driftAnswer.get("citations").getAsArray().getFirst()
                    .getAsObject().get("uri").getAsString().value());
        } finally {
            server.stop();
        }
    }

    @Test
    public void realProviders_qualifyEnrichedModesAndDriftLimits() throws Exception {
        Map<String, String> environment = System.getenv();
        ExternalProviderPrerequisites.requireRealProviderEnvironment(environment);
        assertRuntimeConfiguration(environment);
        Map<String, String> previousProperties = setDriftLimits(1, 1, 64, 1);
        FusekiServer server = null;
        try {
            server = server(environment, true);
            JsonObject configuration = JSON.parse(get(server, "config").body());
            JsonObject limits = configuration.get("driftLimits").getAsObject();
            assertEquals(1, limits.get("communityTopK").getAsNumber().value().intValue());
            assertEquals(1, limits.get("maxFollowUps").getAsNumber().value().intValue());
            assertEquals(64, limits.get("contextTokenBudget").getAsNumber().value().intValue());
            assertEquals(1, limits.get("localTopK").getAsNumber().value().intValue());

            JsonObject accepted = JSON.parse(post(server, "index", """
                    {"title":"Enriched GraphRAG integration corpus","content":"The Beta service publishes citations for retrieved chunks.","sourceUri":"urn:graphrag:enriched-provider"}
                    """).body());
            JsonObject task = awaitTask(server, accepted.get("taskId").getAsString().value());
            assertEquals("done", task.get("status").getAsString().value(), task.toString());

            assertMode(server, "Beta", "basic", "chunk");
            assertMode(server, "Beta", "local", "relationship");
            assertMode(server, "citations", "global", "community");
            assertDriftWithLimits(server, "citations", limits);
        } finally {
            if ( server != null )
                server.stop();
            restoreProperties(previousProperties);
        }
    }

    private FusekiServer server(Map<String, String> environment) {
        return server(environment, false);
    }

    private FusekiServer server(Map<String, String> environment, boolean enrichedCorpus) {
        Model configuration = ModelFactory.createDefaultModel();
        Resource index = configuration.createResource("urn:graphrag:real:index")
                .addProperty(RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex)
                .addProperty(GraphRAGAssemblerVocab.textIndexDir, tempDir.resolve("text").toString())
                .addProperty(GraphRAGAssemblerVocab.vectorIndexDir, tempDir.resolve("vector").toString())
                .addLiteral(GraphRAGAssemblerVocab.vectorDimension, Integer.parseInt(environment.get(ExternalProviderPrerequisites.EMBEDDING_DIMENSION)));
        index.addProperty(GraphRAGAssemblerVocab.embeddingProvider, provider(configuration, environment,
                GraphRAGAssemblerVocab.HttpEmbeddingProvider, ExternalProviderPrerequisites.EMBEDDING_ENDPOINT,
                ExternalProviderPrerequisites.EMBEDDING_API_KEY, ExternalProviderPrerequisites.EMBEDDING_MODEL));
        index.addProperty(GraphRAGAssemblerVocab.chatProvider, provider(configuration, environment,
                GraphRAGAssemblerVocab.HttpChatCompletionProvider, ExternalProviderPrerequisites.CHAT_ENDPOINT,
                ExternalProviderPrerequisites.CHAT_API_KEY, ExternalProviderPrerequisites.CHAT_MODEL));
        configuration.createResource("urn:graphrag:real:service")
                .addLiteral(GraphRAGAssemblerVocab.enableGraphRAG, true)
                .addProperty(GraphRAGAssemblerVocab.graphragIndex, index);
        org.apache.jena.query.Dataset dataset = DatasetFactory.createTxnMem();
        if ( enrichedCorpus ) {
            org.apache.jena.graphrag.GraphRAGImporter.load(chatCorpusPath(), dataset);
        } else {
            dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
            try {
                dataset.getDefaultModel().createResource("urn:graphrag:real-provider:community")
                        .addProperty(RDF.type, GRAG.Community)
                        .addProperty(GRAG.title, "Real-provider citation community")
                        .addProperty(GRAG.summary, "The GraphRAG citation community publishes cited knowledge.")
                        .addProperty(GRAG.fullContent, "The GraphRAG citation community publishes cited knowledge.");
                dataset.commit();
            } finally {
                dataset.end();
            }
        }
        return FusekiServer.create().port(0).add("/" + DATASET, dataset)
                .parseConfig(configuration).fusekiModules(FusekiModules.create(new GraphRAGModule())).build().start();
    }

    private static Path chatCorpusPath() {
        try {
            return Path.of(RealProviderGraphRAGIT.class.getClassLoader()
                    .getResource("corpus/chat/citation-graph.ttl").toURI());
        } catch (Exception ex) {
            throw new IllegalStateException("Missing enriched GraphRAG integration fixture", ex);
        }
    }

    private static void assertMode(FusekiServer server, String query, String mode, String expectedType) throws Exception {
        JsonObject context = JSON.parse(get(server,
                "context?q=" + queryParameter(query) + "&mode=" + mode + "&topK=1").body());
        assertEquals(mode, context.get("mode").getAsString().value());
        assertTrue(context.get("results").getAsArray().size() >= 1);
        assertEquals(expectedType, context.get("results").getAsArray().getFirst().getAsObject()
                .get("type").getAsString().value());
        JsonObject answer = JSON.parse(get(server,
                "answer?q=" + queryParameter(query) + "&mode=" + mode + "&topK=1").body());
        assertFalse(answer.get("answer").getAsString().value().isBlank());
        assertTrue(answer.get("citations").getAsArray().size() >= 1);
        assertEquals(context.get("results").getAsArray().getFirst().getAsObject().get("uri").getAsString().value(),
                answer.get("citations").getAsArray().getFirst().getAsObject().get("uri").getAsString().value());
    }

    private static void assertDriftWithLimits(FusekiServer server, String query, JsonObject limits) throws Exception {
        JsonObject context = JSON.parse(get(server,
                "context?q=" + queryParameter(query) + "&mode=drift&topK=100").body());
        assertEquals("drift", context.get("mode").getAsString().value());
        assertTrue(context.get("results").getAsArray().size()
                <= limits.get("communityTopK").getAsNumber().value().intValue());
        int words = context.get("results").getAsArray().stream()
                .mapToInt(value -> value.getAsObject().get("sourceText").getAsString().value().trim()
                        .split("\\s+").length)
                .sum();
        assertTrue(words <= limits.get("contextTokenBudget").getAsNumber().value().intValue());
        JsonObject answer = JSON.parse(get(server,
                "answer?q=" + queryParameter(query) + "&mode=drift&topK=100").body());
        assertFalse(answer.get("answer").getAsString().value().isBlank());
        assertTrue(answer.get("followUpCount").getAsNumber().value().intValue()
                <= limits.get("maxFollowUps").getAsNumber().value().intValue());
        assertEquals(context.get("results").getAsArray().getFirst().getAsObject().get("uri").getAsString().value(),
                answer.get("citations").getAsArray().getFirst().getAsObject().get("uri").getAsString().value());
    }

    private static Map<String, String> setDriftLimits(int communityTopK, int maxFollowUps,
                                                       int contextTokenBudget, int localTopK) {
        Map<String, String> previous = new java.util.HashMap<>();
        previous.put("jena.graphrag.drift.communityTopK",
                System.getProperty("jena.graphrag.drift.communityTopK"));
        previous.put("jena.graphrag.drift.maxFollowUps",
                System.getProperty("jena.graphrag.drift.maxFollowUps"));
        previous.put("jena.graphrag.drift.contextTokenBudget",
                System.getProperty("jena.graphrag.drift.contextTokenBudget"));
        previous.put("jena.graphrag.drift.localTopK",
                System.getProperty("jena.graphrag.drift.localTopK"));
        System.setProperty("jena.graphrag.drift.communityTopK", Integer.toString(communityTopK));
        System.setProperty("jena.graphrag.drift.maxFollowUps", Integer.toString(maxFollowUps));
        System.setProperty("jena.graphrag.drift.contextTokenBudget", Integer.toString(contextTokenBudget));
        System.setProperty("jena.graphrag.drift.localTopK", Integer.toString(localTopK));
        return previous;
    }

    private static void restoreProperties(Map<String, String> properties) {
        properties.forEach((name, value) -> {
            if ( value == null )
                System.clearProperty(name);
            else
                System.setProperty(name, value);
        });
    }

    private static Resource provider(Model model, Map<String, String> environment, Resource type, String endpoint,
                                     String apiKey, String providerModel) {
        return model.createResource().addProperty(RDF.type, type)
                .addLiteral(GraphRAGAssemblerVocab.allowExternalCalls, true)
                .addLiteral(GraphRAGAssemblerVocab.endpointEnv, endpoint)
                .addLiteral(GraphRAGAssemblerVocab.apiKeyEnv, apiKey)
                .addLiteral(GraphRAGAssemblerVocab.modelName, environment.get(providerModel))
                .addLiteral(GraphRAGAssemblerVocab.timeoutSeconds, 60);
    }

    private static JsonObject awaitTask(FusekiServer server, String taskId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while ( System.nanoTime() < deadline ) {
            JsonObject task = JSON.parse(get(server, "status?taskId=" + taskId).body());
            String status = task.get("status").getAsString().value();
            if ( "done".equals(status) || "failed".equals(status) )
                return task;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for real-provider indexing task");
    }

    private static HttpResponse<String> get(FusekiServer server, String operation) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(endpoint(server, operation))).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(FusekiServer server, String operation, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server, operation)))
                .header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String endpoint(FusekiServer server, String operation) {
        return "http://localhost:" + server.getPort() + "/" + DATASET + "/graphrag/" + operation;
    }

    private static String queryParameter(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void assertRuntimeConfiguration(Map<String, String> environment) {
        assertProjected(environment, "GRAPHRAG_DEFAULT_MODE", "jena.graphrag.defaultMode");
        assertProjected(environment, "GRAPHRAG_DEFAULT_TOP_K", "jena.graphrag.defaultTopK");
        assertProjected(environment, "GRAPHRAG_MAX_TOP_K", "jena.graphrag.maxTopK");
        assertProjected(environment, "GRAPHRAG_DRIFT_COMMUNITY_TOP_K", "jena.graphrag.drift.communityTopK");
        assertProjected(environment, "GRAPHRAG_DRIFT_MAX_FOLLOW_UPS", "jena.graphrag.drift.maxFollowUps");
        assertProjected(environment, "GRAPHRAG_DRIFT_CONTEXT_TOKEN_BUDGET", "jena.graphrag.drift.contextTokenBudget");
        assertProjected(environment, "GRAPHRAG_DRIFT_LOCAL_TOP_K", "jena.graphrag.drift.localTopK");
        assertProjected(environment, "GRAPHRAG_INDEX_MAX_CONTENT_LENGTH", "jena.graphrag.index.maxContentLength");
        assertProjected(environment, "GRAPHRAG_INGESTION_BASE_URI", "jena.graphrag.ingestion.baseUri");
        assertProjected(environment, "GRAPHRAG_INGESTION_CHUNK_SIZE", "jena.graphrag.ingestion.chunkSize");
        assertProjected(environment, "GRAPHRAG_INGESTION_CHUNK_OVERLAP", "jena.graphrag.ingestion.chunkOverlap");
        assertProjected(environment, "GRAPHRAG_INGESTION_MAX_FILE_SIZE_BYTES", "jena.graphrag.ingestion.maxFileSizeBytes");
    }

    private static void assertProjected(Map<String, String> environment, String variable, String property) {
        assertTrue(Objects.equals(environment.get(variable), System.getProperty(property)),
                "Missing runtime configuration projection: " + property);
    }
}
