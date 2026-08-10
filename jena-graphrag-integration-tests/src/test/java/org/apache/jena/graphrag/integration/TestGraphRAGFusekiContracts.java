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

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.graphrag.fuseki.GraphRAGModule;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGFusekiContracts {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String DATASET = "graphrag";

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