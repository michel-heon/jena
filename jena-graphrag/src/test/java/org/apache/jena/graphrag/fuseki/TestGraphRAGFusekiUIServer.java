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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

/**
 * Tests for EPIC-08 — mode serveur Fuseki complet avec interface web (US-08-01, US-08-05).
 * <p>
 * Tests requiring the Fuseki UI webapp ({@code jena-fuseki-ui}) are skipped automatically
 * when {@code /webapp} is absent from the classpath, so the test suite remains hermetic
 * even without the optional dependency.
 */
public class TestGraphRAGFusekiUIServer {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // ── US-08-01 : mode library — GET / doit retourner 404 (régression) ────────

    @Test
    public void root_returns404_in_library_mode() throws Exception {
        FusekiServer server = libraryServer();
        try {
            assertEquals(404, get(server, "/").statusCode(),
                    "En mode library GET / doit retourner 404 (pas d'interface web)");
        } finally {
            server.stop();
        }
    }

    // ── US-08-01 : mode ui — GET / doit retourner 200 ─────────────────────────

    @Test
    @DisabledIf("webappUnavailable")
    public void root_returns200_in_ui_mode() throws Exception {
        FusekiServer server = uiServer();
        try {
            HttpResponse<String> resp = get(server, "/");
            assertEquals(200, resp.statusCode(),
                    "En mode UI GET / doit retourner 200 (interface Fuseki)");
            String body = resp.body();
            // L'index.html Fuseki contient une balise <title> ou du HTML
            assertTrue(body.contains("<!DOCTYPE") || body.contains("<html") || body.contains("Fuseki"),
                    "La réponse doit être une page HTML Fuseki");
        } finally {
            server.stop();
        }
    }

    // ── US-08-05 : coexistence endpoints GraphRAG + interface Fuseki ──────────

    @Test
    @DisabledIf("webappUnavailable")
    public void graphrag_context_works_in_ui_mode() throws Exception {
        FusekiServer server = uiServer();
        try {
            HttpResponse<String> resp = get(server, "/ds/graphrag/context?q=test");
            assertEquals(200, resp.statusCode(),
                    "En mode UI /graphrag/context doit rester HTTP 200");
            assertTrue(resp.headers().firstValue("content-type").orElse("").contains("application/json"),
                    "La réponse doit être application/json");
        } finally {
            server.stop();
        }
    }

    @Test
    @DisabledIf("webappUnavailable")
    public void graphrag_endpoint_present_in_both_modes() throws Exception {
        // En mode library (sans parseConfig enableGraphRAG) l'endpoint /graphrag/* peut être absent.
        // Ce test vérifie seulement que le mode UI expose bien le module GraphRAG (pas 404).
        FusekiServer ui = uiServer();
        try {
            int status = get(ui, "/ds/graphrag/context?q=test").statusCode();
            assertTrue(status != 404,
                    "/graphrag/context ne doit pas être absent (404) en mode ui");
        } finally {
            ui.stop();
        }
    }

    @Test
    @DisabledIf("webappUnavailable")
    public void ping_endpoint_available_in_ui_mode() throws Exception {
        FusekiServer server = uiServer();
        try {
            HttpResponse<String> resp = get(server, "/$/ping");
            assertEquals(200, resp.statusCode(),
                    "En mode UI /$/ping doit retourner 200");
        } finally {
            server.stop();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Returns true when jena-fuseki-ui is absent from the classpath. */
    static boolean webappUnavailable() {
        return GraphRAGFusekiUIServer.class.getResource("/webapp") == null;
    }

    /** Library mode: no static content, identical to GraphRAGPhase2Server. */
    private static FusekiServer libraryServer() {
        return FusekiServer.create()
                .port(0)
                .add("/ds", DatasetFactory.createTxnMem())
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .build()
                .start();
    }

    /** UI mode: static content served from /webapp classpath resources. */
    private static FusekiServer uiServer() {
        URL webappUrl = GraphRAGFusekiUIServer.class.getResource("/webapp");
        Model config = ModelFactory.createDefaultModel();
        config.createResource("urn:graphrag:ui-test")
              .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        return FusekiServer.create()
                .port(0)
                .add("/ds", DatasetFactory.createTxnMem())
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .enableStats(true)
                .staticFileBase(webappUrl.toString())
                .build()
                .start();
    }

    private static HttpResponse<String> get(FusekiServer server, String path)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getPort() + path))
                .GET().build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
