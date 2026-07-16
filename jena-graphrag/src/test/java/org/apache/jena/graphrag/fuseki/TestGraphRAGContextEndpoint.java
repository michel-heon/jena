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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ServiceLoader;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGContextEndpoint {

    private static final String SOURCE = "/org/apache/jena/graphrag/graphrag-sample-source.ttl";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    public void module_isDiscoverableThroughJavaSPI() {
        boolean found = ServiceLoader.load(FusekiModule.class).stream()
                .anyMatch(provider -> provider.type().equals(GraphRAGModule.class));
        assertTrue(found);
    }

    @Test
    public void get_returnsCitedLocalContext() throws Exception {
        FusekiServer server = server(dataset(), true);
        try {
            HttpResponse<String> response = get(server, "?q=scrooge&mode=local&topK=5");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
            JsonObject body = JSON.parse(response.body());
            JsonObject result = body.get("results").getAsArray().getFirst().getAsObject();
            assertEquals("MARLEY", result.get("neighborName").getAsString().value());
            assertTrue(result.get("sourceText").getAsString().value().contains("partner"));
            assertTrue(result.get("uri").getAsString().value().contains("related_to_0001"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void post_emptyDatasetReturnsEmptyResults() throws Exception {
        FusekiServer server = server(DatasetFactory.createTxnMem(), true);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server) + "?q=test"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            JsonArray results = JSON.parse(response.body()).get("results").getAsArray();
            assertTrue(results.isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    public void invalidModeReturnsBadRequest() throws Exception {
        FusekiServer server = server(dataset(), true);
        try {
            HttpResponse<String> response = get(server, "?q=scrooge&mode=global");
            assertEquals(400, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
            JsonObject body = JSON.parse(response.body());
            assertEquals("mode invalide: global", body.get("error").getAsString().value());
        } finally {
            server.stop();
        }
    }

    @Test
    public void disabledModuleDoesNotExposeEndpoint() throws Exception {
        FusekiServer server = server(dataset(), false);
        try {
            HttpResponse<String> response = get(server, "?q=scrooge");
            assertEquals(404, response.statusCode());
        } finally {
            server.stop();
        }
    }

    private static FusekiServer server(Dataset dataset, boolean enabled) {
        Model config = ModelFactory.createDefaultModel();
        if ( enabled ) {
            config.createResource("urn:graphrag:test")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        }
        return FusekiServer.create()
                .port(0)
                .add("/ds", dataset)
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .build()
                .start();
    }

    private static Dataset dataset() {
        Model source = ModelFactory.createDefaultModel();
        try (InputStream in = TestGraphRAGContextEndpoint.class.getResourceAsStream(SOURCE)) {
            source.read(in, null, "TURTLE");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Dataset dataset = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(source, dataset);
        return dataset;
    }

    private static HttpResponse<String> get(FusekiServer server, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server) + query)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String endpoint(FusekiServer server) {
        return "http://localhost:" + server.getPort() + "/ds/graphrag/context";
    }
}