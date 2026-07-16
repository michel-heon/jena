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

package org.apache.jena.graphrag.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGContextService {

    private static final String SOURCE = "/org/apache/jena/graphrag/graphrag-sample-source.ttl";

    private static Dataset dataset() {
        Model source = ModelFactory.createDefaultModel();
        try (InputStream in = TestGraphRAGContextService.class.getResourceAsStream(SOURCE)) {
            source.read(in, null, "TURTLE");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Dataset dataset = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(source, dataset);
        return dataset;
    }

    @Test
    public void retrieve_returnsCitedLocalContext() {
        Dataset dataset = dataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "scrooge", 5);

            assertEquals("local", context.mode());
            assertEquals(1, context.results().size());
            GraphRAGContext.Result result = context.results().getFirst();
            assertEquals("MARLEY", result.neighborName());
            assertTrue(result.sourceText().contains("partner"));
            assertTrue(result.uri().contains("related_to_0001"));
            assertEquals(223.0, result.weight());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieve_emptyDatasetReturnsNoResults() {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "test", 5);
            assertTrue(context.results().isEmpty());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieve_validatesParameters() {
        Dataset dataset = DatasetFactory.createTxnMem();
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), " ", 5));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), "test", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), "test", 101));
    }
}