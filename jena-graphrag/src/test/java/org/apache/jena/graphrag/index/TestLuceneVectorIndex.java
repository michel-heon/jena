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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestLuceneVectorIndex {

    @Test
    public void search_returnsFiveNearestNeighborsFromSyntheticCorpus() {
        try (LuceneVectorIndex index = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            index.index("urn:chunk:1", new float[] { 1.0f, 0.0f });
            index.index("urn:chunk:2", new float[] { 0.9f, 0.1f });
            index.index("urn:chunk:3", new float[] { 0.8f, 0.2f });
            index.index("urn:chunk:4", new float[] { 0.7f, 0.3f });
            index.index("urn:chunk:5", new float[] { 0.6f, 0.4f });
            index.index("urn:chunk:6", new float[] { 0.0f, 1.0f });

            List<VectorResult> results = index.search(new float[] { 1.0f, 0.0f }, 5);

            assertEquals(5, results.size());
            assertEquals(List.of("urn:chunk:1", "urn:chunk:2", "urn:chunk:3", "urn:chunk:4", "urn:chunk:5"),
                    results.stream().map(VectorResult::uri).toList());
            assertTrue(results.getFirst().score() >= results.getLast().score());
        }
    }

    @Test
    public void constructor_rejectsDimensionsGreaterThanLuceneLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new LuceneVectorIndex(new ByteBuffersDirectory(), 1025, VectorSimilarityFunction.EUCLIDEAN));
    }

    @Test
    public void index_rejectsVectorWithUnexpectedDimension() {
        try (LuceneVectorIndex index = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            assertThrows(IllegalArgumentException.class,
                    () -> index.index("urn:chunk:1", new float[] { 1.0f, 0.0f, 0.0f }));
        }
    }

    @Test
    public void contains_returnsWhetherChunkUriIsAlreadyIndexed() {
        try (LuceneVectorIndex index = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            assertTrue(!index.contains("urn:chunk:1"));

            index.index("urn:chunk:1", new float[] { 1.0f, 0.0f });

            assertTrue(index.contains("urn:chunk:1"));
        }
    }
}