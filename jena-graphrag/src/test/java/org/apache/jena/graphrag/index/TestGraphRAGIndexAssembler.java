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

package org.apache.jena.graphrag.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.fuseki.GraphRAGModule;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGraphRAGIndexAssembler {
    private static final String EXAMPLE = "/org/apache/jena/graphrag/graphrag-index-assembler.ttl";

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void init() {
        GraphRAGAssembler.init();
    }

    @Test
    public void open_withEnableGraphRAG_createsTextAndVectorIndexes() {
        Path textDir = tempDir.resolve("text-index");
        Path vectorDir = tempDir.resolve("vector-index");

        try (GraphRAGIndex graphRAGIndex = (GraphRAGIndex) Assembler.general().open(indexSpec(textDir, vectorDir, true))) {
            assertTrue(graphRAGIndex.enabled());
            assertEquals(textDir, graphRAGIndex.textIndexDirectory());
            assertEquals(vectorDir, graphRAGIndex.vectorIndexDirectory());
            assertEquals(2, graphRAGIndex.vectorDimension());
            assertNotNull(graphRAGIndex.textIndex());
            assertNotNull(graphRAGIndex.vectorIndex());
            assertTrue(Files.isDirectory(textDir));
            assertTrue(Files.isDirectory(vectorDir));
        }
    }

    @Test
    public void open_withoutEnableGraphRAG_doesNotCreateIndexes() {
        Path textDir = tempDir.resolve("text-index-disabled");
        Path vectorDir = tempDir.resolve("vector-index-disabled");

        try (GraphRAGIndex graphRAGIndex = (GraphRAGIndex) Assembler.general().open(indexSpec(textDir, vectorDir, false))) {
            assertFalse(graphRAGIndex.enabled());
            assertFalse(Files.exists(textDir));
            assertFalse(Files.exists(vectorDir));
        }
    }

    @Test
    public void open_enabledIndexWithoutVectorDir_failsFast() {
        Model model = ModelFactory.createDefaultModel();
        model.createResource("urn:service")
             .addLiteral(GraphRAGAssemblerVocab.enableGraphRAG, true);
        Resource index = model.createResource("urn:index")
                .addProperty(RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex)
                .addProperty(GraphRAGAssemblerVocab.textIndexDir, tempDir.resolve("text-index").toString());

        assertThrows(AssemblerException.class, () -> Assembler.general().open(index));
    }

    @Test
    public void fusekiStartup_withConfiguredGraphRAGIndex_createsIndexesOnDisk() {
        Path textDir = tempDir.resolve("server-text-index");
        Path vectorDir = tempDir.resolve("server-vector-index");
        Model config = configModel(textDir, vectorDir, true);

        FusekiServer server = FusekiServer.create()
                .port(0)
                .add("/ds", DatasetFactory.createTxnMem())
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .build()
                .start();
        try {
            assertTrue(Files.isDirectory(textDir));
            assertTrue(Files.isDirectory(vectorDir));
        } finally {
            server.stop();
        }
    }

    @Test
    public void turtleExample_loadsWithoutSyntaxErrors() throws Exception {
        try (InputStream in = TestGraphRAGIndexAssembler.class.getResourceAsStream(EXAMPLE)) {
            assertNotNull(in, "GraphRAG index assembler example is missing");
            Model model = ModelFactory.createDefaultModel();
            model.read(in, "https://example.test/graphrag-index-assembler", "TURTLE");
            assertTrue(model.contains(null, RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex));
        }
    }

    private static Resource indexSpec(Path textDir, Path vectorDir, boolean enabled) {
        Model model = configModel(textDir, vectorDir, enabled);
        return model.listObjectsOfProperty(GraphRAGAssemblerVocab.graphragIndex).next().asResource();
    }

    private static Model configModel(Path textDir, Path vectorDir, boolean enabled) {
        Model model = ModelFactory.createDefaultModel();
        Resource index = model.createResource("urn:index")
                .addProperty(RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex)
                .addProperty(GraphRAGAssemblerVocab.textIndexDir, textDir.toString())
                .addProperty(GraphRAGAssemblerVocab.vectorIndexDir, vectorDir.toString())
                .addLiteral(GraphRAGAssemblerVocab.vectorDimension, 2);
        Resource service = model.createResource("urn:service")
                .addProperty(GraphRAGAssemblerVocab.graphragIndex, index);
        if ( enabled )
            service.addLiteral(GraphRAGAssemblerVocab.enableGraphRAG, true);
        return model;
    }
}