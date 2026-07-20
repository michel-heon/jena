/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

import java.net.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.mgt.FusekiServerCtl;
import org.apache.jena.fuseki.mod.shiro.FMod_Shiro;
import org.apache.jena.http.auth.AuthLib;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.GRAG;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GraphRAGSecurityIT {
    private static final HttpClient ANONYMOUS_CLIENT = HttpClient.newHttpClient();

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    public void clearFusekiState() {
        FusekiServerCtl.clearUpSystemState();
    }

    @Test
    public void shiroProtectsMutatingAndConfigurationEndpoints() throws Exception {
        Dataset dataset = DatasetFactory.createTxnMem();
        FusekiServer server = server(dataset);
        try {
            assertUnauthorized(send(ANONYMOUS_CLIENT, request(server, "index").POST(HttpRequest.BodyPublishers.ofString(""))));
            assertUnauthorized(send(ANONYMOUS_CLIENT, request(server, "answer?q=test").GET()));
            assertUnauthorized(send(ANONYMOUS_CLIENT, request(server, "config").GET()));
            assertEquals(0, countDocuments(dataset));

            HttpClient authenticatedClient = authenticatedClient();
            HttpRequest indexRequest = request(server, "index")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"title":"Secured","content":"accepted","sourceUri":"urn:test:secured"}
                            """))
                    .build();
            assertEquals(202, authenticatedClient.send(indexRequest, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, send(authenticatedClient, request(server, "config").GET()).statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    public void readEndpointsRemainPublicWhenConfiguredAnonymous() throws Exception {
        FusekiServer server = server(DatasetFactory.createTxnMem());
        try {
            assertEquals(200, send(ANONYMOUS_CLIENT, request(server, "context?q=test").GET()).statusCode());
            assertEquals(200, send(ANONYMOUS_CLIENT, request(server, "search?q=test").GET()).statusCode());
        } finally {
            server.stop();
        }
    }

    private FusekiServer server(Dataset dataset) throws Exception {
        Path shiroConfiguration = temporaryDirectory.resolve("shiro.ini");
        Files.writeString(shiroConfiguration, """
                [main]
                plainMatcher=org.apache.shiro.authc.credential.SimpleCredentialsMatcher

                [users]
                operator=change-me

                [urls]
                /ds/graphrag/index = authcBasic,user[operator]
                /ds/graphrag/answer = authcBasic,user[operator]
                /ds/graphrag/config = authcBasic,user[operator]
                /ds/graphrag/context = anon
                /ds/graphrag/search = anon
                /** = anon
                """);
        Lib.setenv(FusekiServerCtl.envFusekiShiro, shiroConfiguration.toString());

        Model config = ModelFactory.createDefaultModel();
        config.createResource("urn:graphrag:test")
                .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        FusekiModules modules = FusekiModules.create(FMod_Shiro.create(), new GraphRAGModule());
        return FusekiServer.create().port(0).add("/ds", dataset).parseConfig(config)
                .fusekiModules(modules).build().start();
    }

    private static HttpRequest.Builder request(FusekiServer server, String operation) {
        return HttpRequest.newBuilder(URI.create(
                "http://localhost:" + server.getPort() + "/ds/graphrag/" + operation));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertUnauthorized(HttpResponse<String> response) {
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").isPresent());
    }

    private static HttpClient authenticatedClient() {
        Authenticator authenticator = AuthLib.authenticator("operator", "change-me");
        return HttpClient.newBuilder().authenticator(authenticator).build();
    }

    private static long countDocuments(Dataset dataset) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution queryExecution = QueryExecution.dataset(dataset).query("""
                SELECT (COUNT(?document) AS ?count) WHERE {
                  ?document a <%s> .
                }
                """.formatted(GRAG.Document.getURI())).build()) {
            return queryExecution.execSelect().nextSolution().getLiteral("count").getLong();
        } finally {
            dataset.end();
        }
    }
}