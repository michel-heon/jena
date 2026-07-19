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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
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

public class TestGraphRAGSearchEndpoint {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    public void get_returnsTextVectorAndHybridScoresSortedByHybridScore() throws Exception {
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            FusekiServer server = server(dataset(), true, 0.5, service(vectorIndex));
            try {
                HttpResponse<String> response = get(server, "?q=alpha&topK=3");

                assertEquals(200, response.statusCode());
                assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
                JsonArray results = JSON.parse(response.body()).get("results").getAsArray();
                assertEquals(3, results.size());
                JsonObject result = results.get(0).getAsObject();
                assertNotNull(result.get("uri"));
                assertNotNull(result.get("scoreText"));
                assertNotNull(result.get("scoreVector"));
                assertNotNull(result.get("scoreHybrid"));
                assertEquals(0.5 * number(result, "scoreText") + 0.5 * number(result, "scoreVector"),
                        number(result, "scoreHybrid"), 0.0001);
                assertSortedByHybridScore(results);
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void get_alphaOneExposesTextScoresAsHybridScores() throws Exception {
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            FusekiServer server = server(dataset(), true, 1.0, service(vectorIndex));
            try {
                JsonArray results = JSON.parse(get(server, "?q=alpha&topK=3").body()).get("results").getAsArray();

                for ( int i = 0; i < results.size(); i++ ) {
                    JsonObject result = results.get(i).getAsObject();
                    assertEquals(number(result, "scoreText"), number(result, "scoreHybrid"), 0.0001);
                }
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void get_alphaZeroExposesVectorScoresAsHybridScores() throws Exception {
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            FusekiServer server = server(dataset(), true, 0.0, service(vectorIndex));
            try {
                JsonArray results = JSON.parse(get(server, "?q=alpha&topK=3").body()).get("results").getAsArray();

                for ( int i = 0; i < results.size(); i++ ) {
                    JsonObject result = results.get(i).getAsObject();
                    assertEquals(number(result, "scoreVector"), number(result, "scoreHybrid"), 0.0001);
                }
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void missingQueryReturnsBadRequest() throws Exception {
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            FusekiServer server = server(dataset(), true, 0.5, service(vectorIndex));
            try {
                HttpResponse<String> response = get(server, "?topK=3");

                assertEquals(400, response.statusCode());
                assertEquals("parametre 'q' requis", JSON.parse(response.body()).get("error").getAsString().value());
            } finally {
                server.stop();
            }
        }
    }

    @Test
    public void disabledModuleDoesNotExposeSearchEndpoint() throws Exception {
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            FusekiServer server = server(dataset(), false, 0.5, service(vectorIndex));
            try {
                HttpResponse<String> response = get(server, "?q=alpha");

                assertEquals(404, response.statusCode());
            } finally {
                server.stop();
            }
        }
    }

    private static FusekiServer server(Dataset dataset, boolean enabled, double hybridAlpha, GraphRAGSearchService service) {
        Model config = ModelFactory.createDefaultModel();
        if ( enabled ) {
            config.createResource("urn:graphrag:test")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true)
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "hybridAlpha"), hybridAlpha);
        }
        GraphRAGModule module = new GraphRAGModule((datasetGraph, configuration) ->
                new GraphRAGSearchAction(datasetGraph, configuration, service));
        return FusekiServer.create()
                .port(0)
                .add("/ds", dataset)
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(module))
                .build()
                .start();
    }

    private static GraphRAGSearchService service(LuceneVectorIndex vectorIndex) {
        return new GraphRAGSearchService(vectorIndex, new QueryEmbeddingProvider(), 2);
    }

    private static Dataset dataset() {
        Dataset base = DatasetFactory.createTxnMem();
        Dataset dataset = GraphRAGTextDatasetFactory.createChunkTextDataset(base, new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            addChunk(dataset, "urn:chunk:text", "alpha exact lexical match");
            addChunk(dataset, "urn:chunk:both", "alpha vector lexical overlap");
            addChunk(dataset, "urn:chunk:vector", "semantic vector neighbor");
            dataset.commit();
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static LuceneVectorIndex vectorIndex() {
        LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN);
        vectorIndex.index("urn:chunk:text", new float[] { 0.0f, 1.0f });
        vectorIndex.index("urn:chunk:both", new float[] { 0.8f, 0.2f });
        vectorIndex.index("urn:chunk:vector", new float[] { 1.0f, 0.0f });
        return vectorIndex;
    }

    private static void addChunk(Dataset dataset, String uri, String text) {
        Resource chunk = dataset.getDefaultModel().createResource(uri);
        chunk.addProperty(RDF.type, GRAG.Chunk)
             .addProperty(GRAG.text, text);
    }

    private static HttpResponse<String> get(FusekiServer server, String query) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server) + query)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String endpoint(FusekiServer server) {
        return "http://localhost:" + server.getPort() + "/ds/graphrag/search";
    }

    private static double number(JsonObject object, String key) {
        return object.get(key).getAsNumber().value().doubleValue();
    }

    private static void assertSortedByHybridScore(JsonArray results) {
        for ( int i = 1; i < results.size(); i++ ) {
            double previous = number(results.get(i - 1).getAsObject(), "scoreHybrid");
            double current = number(results.get(i).getAsObject(), "scoreHybrid");
            assertTrue(previous >= current);
        }
    }

    private static final class QueryEmbeddingProvider implements EmbeddingProvider {
        @Override
        public float[] embed(String text, int dimension) {
            return new float[] { 1.0f, 0.0f };
        }
    }
}