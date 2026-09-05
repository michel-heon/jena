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

/**
 * Foreground Fuseki server launched from a TTL assembler while explicitly wiring
 * the GraphRAG Fuseki module and the embedded Fuseki web interface.
 *
 * <p>The assembler retains responsibility for persistent datasets and HTTP GraphRAG
 * providers. This class adds only the web resources and management routes required
 * to make those services accessible from the standard Fuseki UI.</p>
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
        FusekiServer server = prepare(assembler);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "graphrag-network-stop"));
        server.start();
        System.out.printf("Fuseki network: http://localhost:%d/%n", server.getPort());
        server.join();
    }

    static FusekiServer prepare(Path assembler) {
        if ( !Files.isRegularFile(assembler) )
            throw new IllegalArgumentException("Assembleur introuvable: " + assembler);

        URL webappUrl = GraphRAGNetworkServer.class.getResource("/webapp");
        if ( webappUrl == null )
            throw new IllegalStateException(
                    "Fuseki UI introuvable sur le classpath — ajoutez jena-fuseki-ui comme dependance runtime.");

        return FusekiServer.create()
                .parseConfigFile(assembler.toString())
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .enablePing(true)
                .enableStats(true)
                .enableTasks(true)
                .addServlet(Fuseki.serverFunctionPath("/datasets/*"), new ActionDatasets())
                .addServlet(Fuseki.serverFunctionPath("/server"), new ActionServerStatus())
                .staticFileBase(webappUrl.toString())
                .build();
    }
}