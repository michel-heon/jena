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

/**
 * Foreground Fuseki server launched from a TTL assembler while explicitly wiring
 * the GraphRAG Fuseki module.
 */
public final class GraphRAGNetworkServer {

    private GraphRAGNetworkServer() {}

    /**
     * Starts Fuseki from an assembler file and keeps the process in foreground.
     *
     * @param args {@code <assembler.ttl>}
     */
    public static void main(String... args) {
        if ( args.length != 1 )
            throw new IllegalArgumentException("Usage: GraphRAGNetworkServer <assembler.ttl>");

        Path assembler = Path.of(args[0]);
        if ( !Files.isRegularFile(assembler) )
            throw new IllegalArgumentException("Assembleur introuvable: " + assembler);

        FusekiServer server = FusekiServer.create()
                .parseConfigFile(assembler.toString())
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "graphrag-network-stop"));
        server.start();
        System.out.printf("Fuseki network: http://localhost:%d/%n", server.getPort());
        server.join();
    }
}