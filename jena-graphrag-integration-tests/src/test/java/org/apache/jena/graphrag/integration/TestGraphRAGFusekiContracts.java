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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.assembler.Assembler;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.graphrag.fuseki.GraphRAGModule;
import org.apache.jena.graphrag.index.GraphRAGAssembler;
import org.apache.jena.graphrag.index.GraphRAGAssemblerVocab;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGraphRAGFusekiContracts {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String DATASET = "graphrag";

    @TempDir
    Path tempDir;

    @Test
    public void enabledServer_exposesPingConfigurationContextAndStructuredErrors() throws Exception {
        FusekiServer server = server(true);
        try {
            assertEquals(200, get(server, "/$/ping").statusCode());

            HttpResponse<String> config = get(server, "/" + DATASET + "/graphrag/config");
            assertEquals(200, config.statusCode());
            assertTrue(config.headers().firstValue("content-type").orElse("").contains("application/json"));
            assertTrue(JSON.parse(config.body()).get("enabled").getAsBoolean().value());

            HttpResponse<String> context = get(server,
                    "/" + DATASET + "/graphrag/context?q=alpha&mode=local&topK=1");
            assertEquals(200, context.statusCode());
            JsonObject contextBody = JSON.parse(context.body());
            assertEquals("local", contextBody.get("mode").getAsString().value());
            assertEquals("Beta", contextBody.get("results").getAsArray().getFirst().getAsObject()
                    .get("neighborName").getAsString().value());

            HttpResponse<String> invalid = get(server, "/" + DATASET + "/graphrag/context?q=alpha&mode=unknown");
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.headers().firstValue("content-type").orElse("").contains("application/json"));
            assertTrue(JSON.parse(invalid.body()).hasKey("error"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void disabledServer_keepsPingButDoesNotExposeGraphRAGRoutes() throws Exception {
        FusekiServer server = server(false);
        try {
            assertEquals(200, get(server, "/$/ping").statusCode());
            assertEquals(404, get(server, "/" + DATASET + "/graphrag/config").statusCode());
            assertEquals(404, get(server, "/" + DATASET + "/graphrag/context?q=alpha").statusCode());
            assertEquals(404, get(server, "/" + DATASET + "/graphrag/search?q=alpha").statusCode());
            assertEquals(404, get(server, "/" + DATASET + "/graphrag/answer?q=alpha").statusCode());
            assertEquals(404, get(server, "/" + DATASET + "/graphrag/status?taskId=absent").statusCode());
            assertEquals(404, post(server, "/" + DATASET + "/graphrag/index", "{}").statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    public void enabledServer_validatesProviderFreeGraphRAGContracts() throws Exception {
        FusekiServer server = server(true);
        try {
            HttpResponse<String> search = get(server, "/" + DATASET + "/graphrag/search?topK=1");
            assertEquals(400, search.statusCode());
            assertEquals("parametre 'q' requis", JSON.parse(search.body()).get("error").getAsString().value());

            HttpResponse<String> answer = get(server, "/" + DATASET + "/graphrag/answer");
            assertEquals(400, answer.statusCode());
            JsonObject answerError = JSON.parse(answer.body()).get("error").getAsObject();
            assertEquals("invalid_request", answerError.get("code").getAsString().value());

            HttpResponse<String> index = post(server, "/" + DATASET + "/graphrag/index", "{}");
            assertEquals(400, index.statusCode());
            assertEquals("invalid_request", JSON.parse(index.body()).get("error").getAsObject()
                    .get("code").getAsString().value());

            HttpResponse<String> status = get(server, "/" + DATASET + "/graphrag/status?taskId=absent");
            assertEquals(404, status.statusCode());
            assertEquals("task_not_found", JSON.parse(status.body()).get("error").getAsObject()
                    .get("code").getAsString().value());
        } finally {
            server.stop();
        }
    }

    @Test
    public void enabledServer_reportsIndexingProgressThroughTaskApi() throws Exception {
        FusekiServer server = serverWithIndex();
        try {
            HttpResponse<String> accepted = post(server, "/" + DATASET + "/graphrag/index",
                    "{\"title\":\"Progress fixture\",\"content\":\"One indexed chunk\",\"sourceUri\":\"urn:test:progress\"}");
            assertEquals(202, accepted.statusCode());
            String taskId = JSON.parse(accepted.body()).get("taskId").getAsString().value();

            JsonObject task = awaitTerminalTask(server, taskId);
            JsonObject progress = task.get("progress").getAsObject();
            int totalChunks = progress.get("totalChunks").getAsNumber().value().intValue();
            int chunksIndexed = progress.get("chunksIndexed").getAsNumber().value().intValue();
            int percentComplete = progress.get("percentComplete").getAsNumber().value().intValue();

            assertEquals("done", task.get("status").getAsString().value());
            assertEquals(1, totalChunks);
            assertEquals(totalChunks, chunksIndexed);
            assertEquals(100, percentComplete);
        } finally {
            server.stop();
        }
    }

    private static FusekiServer server(boolean enabled) {
        Dataset dataset = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(corpusPath(), dataset);
        Model configuration = ModelFactory.createDefaultModel();
        if ( enabled ) {
            configuration.createResource("urn:graphrag:integration")
                    .addLiteral(configuration.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        }
        return FusekiServer.create().port(0).add("/" + DATASET, dataset)
                .parseConfig(configuration)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .build()
                .start();
    }

    private FusekiServer serverWithIndex() {
        GraphRAGAssembler.init();
        Dataset dataset = DatasetFactory.createTxnMem();
        Model configuration = ModelFactory.createDefaultModel();
        Resource index = configuration.createResource("urn:graphrag:progress:index")
                .addProperty(RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex)
                .addProperty(GraphRAGAssemblerVocab.textIndexDir, tempDir.resolve("text").toString())
                .addProperty(GraphRAGAssemblerVocab.vectorIndexDir, tempDir.resolve("vector").toString())
                .addLiteral(GraphRAGAssemblerVocab.vectorDimension, 2)
                .addProperty(GraphRAGAssemblerVocab.embeddingProvider,
                        configuration.createResource().addProperty(RDF.type, GraphRAGAssemblerVocab.MockEmbeddingProvider));
        configuration.createResource("urn:graphrag:progress:service")
                .addLiteral(GraphRAGAssemblerVocab.enableGraphRAG, true)
                .addProperty(GraphRAGAssemblerVocab.graphragIndex, index);
        return FusekiServer.create().port(0).add("/" + DATASET, dataset)
                .parseConfig(configuration)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .build()
                .start();
    }

    private static JsonObject awaitTerminalTask(FusekiServer server, String taskId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        JsonObject last = null;
        while ( System.nanoTime() < deadline ) {
            HttpResponse<String> response = get(server,
                    "/" + DATASET + "/graphrag/status?taskId=" + taskId);
            assertEquals(200, response.statusCode());
            last = JSON.parse(response.body());
            String status = last.get("status").getAsString().value();
            JsonObject progress = last.get("progress").getAsObject();
            int totalChunks = progress.get("totalChunks").getAsNumber().value().intValue();
            int chunksIndexed = progress.get("chunksIndexed").getAsNumber().value().intValue();
            int percentComplete = progress.get("percentComplete").getAsNumber().value().intValue();
            assertTrue(totalChunks >= 0);
            assertTrue(chunksIndexed >= 0 && chunksIndexed <= totalChunks);
            assertTrue(percentComplete >= 0 && percentComplete <= 100);
            if ( "done".equals(status) || "failed".equals(status) )
                return last;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for indexing task: " + last);
    }

    private static Path corpusPath() {
        try {
            return Path.of(TestGraphRAGFusekiContracts.class.getClassLoader()
                    .getResource("corpus/ingestion/graphrag-import.ttl").toURI());
        } catch (Exception ex) {
            throw new IllegalStateException("Missing GraphRAG RDF integration fixture", ex);
        }
    }

    private static HttpResponse<String> get(FusekiServer server, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getPort() + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(FusekiServer server, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getPort() + path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}