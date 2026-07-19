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
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.store.ByteBuffersDirectory;
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
    public void get_globalModeMatchesNativeSparqlTop3() throws Exception {
        Dataset dataset = globalDataset();
        String[] expected = nativeGlobalTop3(dataset, "energy");
        FusekiServer server = server(dataset, true);
        try {
            HttpResponse<String> response = get(server, "?q=energy&mode=global&topK=3");

            assertEquals(200, response.statusCode());
            JsonObject body = JSON.parse(response.body());
            assertEquals("global", body.get("mode").getAsString().value());
            JsonArray results = body.get("results").getAsArray();
            assertEquals(expected.length, results.size());
            for ( int i = 0; i < expected.length; i++ ) {
                JsonObject result = results.get(i).getAsObject();
                assertEquals(expected[i], result.get("uri").getAsString().value());
                assertEquals("community", result.get("type").getAsString().value());
                assertEquals(expected[i], result.get("communityUri").getAsString().value());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void get_globalModeEmptyDatasetReturnsEmptyResults() throws Exception {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        FusekiServer server = server(dataset, true);
        try {
            HttpResponse<String> response = get(server, "?q=energy&mode=global");

            assertEquals(200, response.statusCode());
            JsonObject body = JSON.parse(response.body());
            assertEquals("global", body.get("mode").getAsString().value());
            assertTrue(body.get("results").getAsArray().isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    public void invalidModeReturnsBadRequest() throws Exception {
        FusekiServer server = server(dataset(), true);
        try {
            HttpResponse<String> response = get(server, "?q=scrooge&mode=drift");
            assertEquals(400, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
            JsonObject body = JSON.parse(response.body());
            assertEquals("mode invalide: drift", body.get("error").getAsString().value());
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

    private static Dataset globalDataset() {
        Dataset base = DatasetFactory.createTxnMem();
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(base, new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            addCommunity(dataset, "urn:community:energy-1", "Energy planning north",
                    "Energy reliability planning", "Grid resilience for northern districts");
            addCommunity(dataset, "urn:community:energy-2", "Energy planning south",
                    "Energy reliability operations", "Storage resilience for southern districts");
            addCommunity(dataset, "urn:community:energy-3", "Energy finance",
                    "Energy investment oversight", "Capital planning for infrastructure");
            dataset.commit();
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static void addCommunity(Dataset dataset, String uri, String title, String summary, String fullContent) {
        Resource community = dataset.getDefaultModel().createResource(uri);
        community.addProperty(RDF.type, GRAG.Community)
                 .addProperty(GRAG.title, title)
                 .addProperty(GRAG.summary, summary)
                 .addProperty(GRAG.fullContent, fullContent);
    }

    private static String[] nativeGlobalTop3(Dataset dataset, String query) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qexec = QueryExecution.dataset(dataset).query("""
                PREFIX text: <http://jena.apache.org/text#>
                PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                SELECT ?community ?score WHERE {
                  (?community ?score) text:query (mg:summary ?query 3) .
                  ?community a mg:Community .
                }
                ORDER BY DESC(?score) STR(?community)
                """)
                .substitution("query", dataset.getDefaultModel().createLiteral(query))
                .build()) {
            ResultSet results = qexec.execSelect();
            java.util.ArrayList<String> uris = new java.util.ArrayList<>();
            while ( results.hasNext() )
                uris.add(results.next().getResource("community").getURI());
            return uris.toArray(String[]::new);
        } finally {
            dataset.end();
        }
    }

    private static HttpResponse<String> get(FusekiServer server, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server) + query)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String endpoint(FusekiServer server) {
        return "http://localhost:" + server.getPort() + "/ds/graphrag/context";
    }
}