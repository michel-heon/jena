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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestChunkVectorIndexer {

    @Test
    public void indexChunk_usesChunkUriAsStableVectorKeyAndSkipsRecalculation() {
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 4, VectorSimilarityFunction.EUCLIDEAN)) {
            DeterministicEmbeddingProvider provider = new DeterministicEmbeddingProvider();
            ChunkVectorIndexer indexer = new ChunkVectorIndexer(vectorIndex, provider, 4);

            assertTrue(indexer.indexChunk("urn:chunk:1", "Scrooge saw Marley."));
            assertTrue(vectorIndex.contains("urn:chunk:1"));
            assertFalse(indexer.indexChunk("urn:chunk:1", "Changed text must not be embedded again."));

            assertEquals(1, provider.calls());
            assertEquals("urn:chunk:1",
                    vectorIndex.search(DeterministicEmbeddingProvider.vectorFor("Scrooge saw Marley.", 4), 1)
                            .getFirst().uri());
        }
    }

    @Test
    public void indexChunk_rejectsEmbeddingWithUnexpectedDimension() {
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 4, VectorSimilarityFunction.EUCLIDEAN)) {
            ChunkVectorIndexer indexer = new ChunkVectorIndexer(vectorIndex, (text, dimension) -> new float[] { 1.0f }, 4);

            assertThrows(IllegalArgumentException.class,
                    () -> indexer.indexChunk("urn:chunk:1", "Scrooge saw Marley."));
        }
    }
}