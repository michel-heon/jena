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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.fuseki.main.FusekiServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * Qualification runtime du serveur Marketplace Fuseki UI / GraphRAG.
 * <p>
 * Ces tests couvrent les contrats observables introduits pour l'image Azure VM :
 * boot sur corpus embarqué, exposition simultanée de l'UI Fuseki et des endpoints
 * GraphRAG, et validation stricte des paramètres de démarrage.
 */
public class TestGraphRAGFusekiUIServerRuntime {

    private static final String SOURCE = "/org/apache/jena/graphrag/graphrag-sample-source.ttl";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Test
    public void settingsRejectInvalidPortDatasetAndActivation() {
        assertThrows(IllegalArgumentException.class,
                () -> GraphRAGFusekiUIServer.Settings.parse(new String[] {
                        "/tmp/corpus.ttl", "0", "ds", "true"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> GraphRAGFusekiUIServer.Settings.parse(new String[] {
                        "/tmp/corpus.ttl", "3030", "dataset invalide", "true"
                }));
        assertThrows(IllegalArgumentException.class,
                () -> GraphRAGFusekiUIServer.Settings.parse(new String[] {
                        "/tmp/corpus.ttl", "3030", "ds", "yes"
                }));
    }

    @Test
    @DisabledIf("webappUnavailable")
    public void serverBootsOnCorpusAndExposesUiPingConfigAndContext() throws Exception {
        Path corpus = copyCorpus();
        GraphRAGFusekiUIServer.ServerBootstrap bootstrap = GraphRAGFusekiUIServer.prepare(
                new GraphRAGFusekiUIServer.Settings(corpus, 0, "ds", true));
        assertTrue(bootstrap.tripleCount() > 0, "Le corpus de qualification doit charger des triplets");

        FusekiServer server = bootstrap.server();
        try {
            server.start();

            HttpResponse<String> root = get(server, "/");
            assertEquals(200, root.statusCode());
            assertTrue(root.body().contains("Fuseki") || root.body().contains("<html"));

            HttpResponse<String> ping = get(server, "/$/ping");
            assertEquals(200, ping.statusCode());

            HttpResponse<String> config = get(server, "/ds/graphrag/config");
            assertEquals(200, config.statusCode());
            JsonObject configBody = JSON.parse(config.body());
            assertEquals(true, configBody.get("enabled").getAsBoolean().value());
            assertTrue(configBody.get("modes").getAsArray().size() >= 3);

            HttpResponse<String> context = get(server, "/ds/graphrag/context?q=scrooge&mode=local&topK=1");
            assertEquals(200, context.statusCode());
            JsonObject contextBody = JSON.parse(context.body());
            JsonObject first = contextBody.get("results").getAsArray().get(0).getAsObject();
            assertEquals("MARLEY", first.get("neighborName").getAsString().value());
            assertTrue(first.get("sourceText").getAsString().value().contains("partner"));
        } finally {
            server.stop();
            Files.deleteIfExists(corpus);
        }
    }

    @Test
    @DisabledIf("webappUnavailable")
    public void disabledGraphRagKeepsUiAvailableButHidesEndpoints() throws Exception {
        Path corpus = copyCorpus();
        GraphRAGFusekiUIServer.ServerBootstrap bootstrap = GraphRAGFusekiUIServer.prepare(
                new GraphRAGFusekiUIServer.Settings(corpus, 0, "ds", false));

        FusekiServer server = bootstrap.server();
        try {
            server.start();

            assertEquals(200, get(server, "/").statusCode());
            assertEquals(200, get(server, "/$/ping").statusCode());
            assertEquals(404, get(server, "/ds/graphrag/config").statusCode());
            assertEquals(404, get(server, "/ds/graphrag/context?q=scrooge").statusCode());
        } finally {
            server.stop();
            Files.deleteIfExists(corpus);
        }
    }

    static boolean webappUnavailable() {
        return GraphRAGFusekiUIServer.class.getResource("/webapp") == null;
    }

    private static HttpResponse<String> get(FusekiServer server, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getPort() + path))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Path copyCorpus() throws IOException {
        Path temp = Files.createTempFile("graphrag-marketplace-", ".ttl");
        try (InputStream in = TestGraphRAGFusekiUIServerRuntime.class.getResourceAsStream(SOURCE)) {
            if (in == null) {
                throw new IOException("Ressource introuvable: " + SOURCE);
            }
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }
}
