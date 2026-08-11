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

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.graphrag.index.GraphRAGAssembler;
import org.apache.jena.graphrag.index.GraphRAGAssemblerVocab;
import org.apache.jena.graphrag.index.GraphRAGIndex;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGraphRAGIndexingService {

    @TempDir
    Path tempDir;

    @Test
    public void configuredIndex_vectorizesChunkCreatedByIndexingTask() throws Exception {
        Dataset dataset = DatasetFactory.createTxnMem();
        GraphRAGAssembler.init();
        try (GraphRAGIndex graphRAGIndex = (GraphRAGIndex) Assembler.general().open(indexSpec())) {
            GraphRAGTaskService taskService = new GraphRAGTaskService(1, 10);
            GraphRAGIndexingService service = new GraphRAGIndexingService(dataset, taskService,
                new GraphRAGConfiguration("local", 5, 100, 0.5), graphRAGIndex);

            GraphRAGTask task = service.submit(new GraphRAGIndexRequest("Test", "Indexed GraphRAG content", "urn:test:source"));
            GraphRAGTask completedTask = awaitCompletion(taskService, task.taskId());

            assertEquals(GraphRAGTaskStatus.DONE, completedTask.status());
            assertTrue(graphRAGIndex.vectorIndex().contains("urn:test:source#chunk-" + task.taskId()));
        }
    }

    private static GraphRAGTask awaitCompletion(GraphRAGTaskService taskService, String taskId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ( System.nanoTime() < deadline ) {
            GraphRAGTask task = taskService.find(taskId).orElseThrow();
            if ( task.status() == GraphRAGTaskStatus.DONE || task.status() == GraphRAGTaskStatus.FAILED )
                return task;
            Thread.sleep(10);
        }
        return taskService.find(taskId).orElseThrow();
    }

    private Resource indexSpec() {
        Model model = ModelFactory.createDefaultModel();
        model.createResource("urn:test:service").addLiteral(GraphRAGAssemblerVocab.enableGraphRAG, true);
        return model.createResource("urn:test:index")
                .addProperty(RDF.type, GraphRAGAssemblerVocab.GraphRAGIndex)
                .addProperty(GraphRAGAssemblerVocab.textIndexDir, tempDir.resolve("text").toString())
                .addProperty(GraphRAGAssemblerVocab.vectorIndexDir, tempDir.resolve("vector").toString())
                .addLiteral(GraphRAGAssemblerVocab.vectorDimension, 2);
    }
}