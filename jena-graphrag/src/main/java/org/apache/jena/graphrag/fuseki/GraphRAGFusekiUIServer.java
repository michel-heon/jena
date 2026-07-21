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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.mgt.ActionDatasets;
import org.apache.jena.fuseki.mod.admin.ActionServerStatus;
import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Foreground Fuseki server in full UI mode (Fuseki web interface + SPARQL Playground).
 * <p>
 * Unlike {@link GraphRAGPhase2Server} which runs in library mode (HTTP 404 at root),
 * this server activates the embedded Fuseki web interface so that:
 * <ul>
 *   <li>{@code GET /} returns HTTP 200 with the Fuseki UI;</li>
 *   <li>{@code GET /{dataset}/sparql} exposes the interactive SPARQL Playground;</li>
 *   <li>all {@code /{dataset}/graphrag/*} endpoints remain fully operational.</li>
 * </ul>
 * <p>
 * Requires {@code jena-fuseki-ui} on the runtime classpath (provides {@code /webapp}
 * classpath resources). If the resource is absent the server refuses to start with
 * a clear error message.
 * <p>
 * Intended for local exploration and debugging only; use {@link GraphRAGPhase2Server}
 * (library mode) for CI and headless deployments.
 */
public final class GraphRAGFusekiUIServer {

    private GraphRAGFusekiUIServer() {}

    /**
     * Starts a Fuseki server with the embedded web UI enabled.
     *
     * @param args {@code <corpus> <port> <dataset> <true|false>}
     * @throws IllegalArgumentException if any argument is invalid
     * @throws IllegalStateException    if {@code jena-fuseki-ui} is not on the classpath
     */
    public static void main(String... args) {
        Settings settings = Settings.parse(args);
        if ( !Files.isRegularFile(settings.corpus()) )
            throw new IllegalArgumentException("Corpus introuvable: " + settings.corpus());

        URL webappUrl = GraphRAGFusekiUIServer.class.getResource("/webapp");
        if ( webappUrl == null )
            throw new IllegalStateException(
                    "Fuseki UI introuvable sur le classpath — ajoutez jena-fuseki-ui comme dependance runtime.");

        Dataset dataset = DatasetFactory.createTxnMem();
        long tripleCount = GraphRAGImporter.load(settings.corpus(), dataset);

        Model config = ModelFactory.createDefaultModel();
        if ( settings.enabled() ) {
            config.createResource("urn:graphrag:ui")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        }

        FusekiServer server = FusekiServer.create()
                .port(settings.port())
                .add("/" + settings.datasetName(), dataset)
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .enableStats(true)
                .enableTasks(true)
                // Admin API — required by the Fuseki web UI to list datasets (/$/datasets)
                .addServlet(Fuseki.serverFunctionPath("/datasets/*"), new ActionDatasets())
                .addServlet(Fuseki.serverFunctionPath("/server"),    new ActionServerStatus())
                .staticFileBase(webappUrl.toString())
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "graphrag-ui-stop"));
        server.start();
        int port = server.getPort();
        System.out.printf("GraphRAG UI: %d triplets normalises%n", tripleCount);
        System.out.printf("Interface Fuseki  : http://localhost:%d/%n", port);
        System.out.printf("SPARQL Playground : http://localhost:%d/%s/sparql%n", port, settings.datasetName());
        System.out.printf("GraphRAG context  : http://localhost:%d/%s/graphrag/context%n", port, settings.datasetName());
        System.out.printf("GraphRAG         : %s%n", settings.enabled() ? "active" : "inactif");
        server.join();
    }

    private record Settings(Path corpus, int port, String datasetName, boolean enabled) {

        private static Settings parse(String[] args) {
            if ( args.length != 4 )
                throw new IllegalArgumentException(
                        "Usage: GraphRAGFusekiUIServer <corpus> <port> <dataset> <true|false>");
            int port;
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Port invalide: " + args[1], ex);
            }
            if ( port < 1 || port > 65535 )
                throw new IllegalArgumentException("Port hors limites: " + port);
            if ( !args[2].matches("[A-Za-z0-9_-]+") )
                throw new IllegalArgumentException("Nom de dataset invalide: " + args[2]);
            if ( !"true".equals(args[3]) && !"false".equals(args[3]) )
                throw new IllegalArgumentException("Activation invalide: " + args[3]);
            return new Settings(Path.of(args[0]), port, args[2], Boolean.parseBoolean(args[3]));
        }
    }
}
