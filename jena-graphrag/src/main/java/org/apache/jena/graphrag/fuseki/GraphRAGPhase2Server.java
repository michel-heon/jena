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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Foreground Fuseki server for the delivered local GraphRAG validation workflow.
 * <p>
 * The server loads an RDF dump into an in-memory dataset, optionally enables the
 * GraphRAG Fuseki module, and blocks until stopped. It is intended for local
 * validation from scripts, not as a production launcher.
 */
public final class GraphRAGPhase2Server {

    private GraphRAGPhase2Server() {}

    /**
     * Starts a local Fuseki server for manual validation.
     *
     * @param args {@code <corpus> <port> <dataset> <true|false>}
     * @throws IllegalArgumentException if the corpus, port, dataset name, or activation flag is invalid
     */
    public static void main(String... args) {
        Settings settings = Settings.parse(args);
        if ( !Files.isRegularFile(settings.corpus()) )
            throw new IllegalArgumentException("Corpus introuvable: " + settings.corpus());

        Dataset dataset = DatasetFactory.createTxnMem();
        long tripleCount = GraphRAGImporter.load(settings.corpus(), dataset);

        Model config = ModelFactory.createDefaultModel();
        if ( settings.enabled() ) {
            config.createResource("urn:graphrag:phase2")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        }

        FusekiServer server = FusekiServer.create()
                .port(settings.port())
                .add("/" + settings.datasetName(), dataset)
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "graphrag-phase2-stop"));
        server.start();
        System.out.printf("GraphRAG Phase 2: %d triplets normalises%n", tripleCount);
        System.out.printf("Fuseki: http://localhost:%d/%s/graphrag/context%n",
                server.getPort(), settings.datasetName());
        System.out.printf("GraphRAG: %s%n", settings.enabled() ? "active" : "inactif");
        server.join();
    }

    private record Settings(Path corpus, int port, String datasetName, boolean enabled) {

        private static Settings parse(String[] args) {
            if ( args.length != 4 )
                throw new IllegalArgumentException(
                        "Usage: GraphRAGPhase2Server <corpus> <port> <dataset> <true|false>");
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