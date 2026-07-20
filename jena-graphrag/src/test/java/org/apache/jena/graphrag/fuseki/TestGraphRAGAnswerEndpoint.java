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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.graphrag.retrieval.GraphRAGSearchService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGAnswerEndpoint {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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
}