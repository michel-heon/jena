/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.fuseki.GraphRAGModule;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGFusekiLifecycle {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String DATASET = "graphrag";
    private static final String DOCUMENT = "urn:graphrag:integration:lifecycle:document";
    private static final String CHUNK = DOCUMENT + "#chunk-0";
    private static final String ORIGINAL_TEXT = "Lifecycle original marker.";
    private static final String REVISED_TEXT = "Lifecycle revised marker.";

    @Test
    public void defaultGraphSupportsImportReadUpdateAndDelete() throws Exception {
        FusekiServer server = server();
        try {
            update(server, """
                    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
                    INSERT DATA {
                      <%s> a grag:Document ; grag:id "lifecycle-document" .
                      <%s> a grag:Chunk ; grag:text "%s" ; grag:partOf <%s> .
                    }
                    """.formatted(DOCUMENT, CHUNK, ORIGINAL_TEXT, DOCUMENT));
            assertEquals(2, countResources(server));
            assertEquals(ORIGINAL_TEXT, readText(server));

            update(server, """
                    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
                    DELETE { <%s> grag:text "%s" }
                    INSERT { <%s> grag:text "%s" }
                    WHERE  { <%s> grag:text "%s" }
                    """.formatted(CHUNK, ORIGINAL_TEXT, CHUNK, REVISED_TEXT, CHUNK, ORIGINAL_TEXT));
            assertEquals(REVISED_TEXT, readText(server));

            update(server, """
                    DELETE {
                      <%s> ?documentPredicate ?documentObject .
                      <%s> ?chunkPredicate ?chunkObject
                    }
                    WHERE {
                      OPTIONAL { <%s> ?documentPredicate ?documentObject }
                      OPTIONAL { <%s> ?chunkPredicate ?chunkObject }
                    }
                    """.formatted(DOCUMENT, CHUNK, DOCUMENT, CHUNK));
            assertEquals(0, countResources(server));
        } finally {
            server.stop();
        }
    }

    private static FusekiServer server() {
        var configuration = ModelFactory.createDefaultModel();
        configuration.createResource("urn:graphrag:lifecycle:service")
                .addLiteral(configuration.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        return FusekiServer.create().port(0).add("/" + DATASET, DatasetFactory.createTxnMem())
                .parseConfig(configuration)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .build()
                .start();
    }

    private static void update(FusekiServer server, String update) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint(server, "update"))
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("update=" + URLEncoder.encode(update, StandardCharsets.UTF_8)))
                .build();
        assertEquals(200, HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private static int countResources(FusekiServer server) throws Exception {
        String query = "SELECT (COUNT(DISTINCT ?resource) AS ?count) WHERE { VALUES ?resource { <"
                + DOCUMENT + "> <" + CHUNK + "> } ?resource a ?type }";
        String body = get(server, "sparql?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=application%2Fsparql-results%2Bjson");
        String count = JSON.parse(body).get("results").getAsObject().get("bindings").getAsArray().getFirst()
                .getAsObject().get("count").getAsObject().get("value").getAsString().value();
        return Integer.parseInt(count);
    }

    private static String readText(FusekiServer server) throws Exception {
        String query = "SELECT ?text WHERE { <" + CHUNK + "> <http://ormynet.com/ns/msft-graphrag#text> ?text }";
        String body = get(server, "sparql?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=application%2Fsparql-results%2Bjson");
        return JSON.parse(body).get("results").getAsObject().get("bindings").getAsArray().getFirst()
                .getAsObject().get("text").getAsObject().get("value").getAsString().value();
    }

    private static String get(FusekiServer server, String operation) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(endpoint(server, operation)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private static URI endpoint(FusekiServer server, String operation) {
        return URI.create("http://localhost:" + server.getPort() + "/" + DATASET + "/" + operation);
    }
}