/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.graphrag.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestCommunityReportVectorIndexer {

    @Test
    public void indexCommunity_usesCommunityUriAndNormalizedReportContent() {
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 4, VectorSimilarityFunction.EUCLIDEAN)) {
            DeterministicEmbeddingProvider provider = new DeterministicEmbeddingProvider();
            CommunityReportVectorIndexer indexer = new CommunityReportVectorIndexer(vectorIndex, provider, 4);

            assertTrue(indexer.indexCommunity("urn:community:1", "  Summary  ", "Full report"));
            assertTrue(vectorIndex.contains("urn:community:1"));
            assertFalse(indexer.indexCommunity("urn:community:1", "Changed", "Changed report"));
            assertEquals(1, provider.calls());
            assertEquals("urn:community:1", vectorIndex.search(
                    DeterministicEmbeddingProvider.vectorFor("Summary\n\nFull report", 4), 1).getFirst().uri());
        }
    }

    @Test
    public void indexCommunity_rejectsEmptyReportAndUnexpectedEmbeddingDimension() {
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 4, VectorSimilarityFunction.EUCLIDEAN)) {
            CommunityReportVectorIndexer emptyReportIndexer = new CommunityReportVectorIndexer(vectorIndex,
                    new DeterministicEmbeddingProvider(), 4);
            assertThrows(IllegalArgumentException.class,
                    () -> emptyReportIndexer.indexCommunity("urn:community:1", " ", null));

            CommunityReportVectorIndexer wrongDimensionIndexer = new CommunityReportVectorIndexer(vectorIndex,
                    (text, dimension) -> new float[] { 1.0f }, 4);
            assertThrows(IllegalArgumentException.class,
                    () -> wrongDimensionIndexer.indexCommunity("urn:community:1", "Summary", null));
        }
    }
}