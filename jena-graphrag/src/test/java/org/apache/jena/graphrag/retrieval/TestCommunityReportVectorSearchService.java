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
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestCommunityReportVectorSearchService {

    @Test
    public void search_returnsOnlyNearestCommunityReports() {
        try (LuceneVectorIndex communityIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2,
                VectorSimilarityFunction.EUCLIDEAN)) {
            communityIndex.index("urn:community:alpha", new float[] { 1.0f, 0.0f });
            communityIndex.index("urn:community:beta", new float[] { 0.0f, 1.0f });
            CommunityReportVectorSearchService service = new CommunityReportVectorSearchService(
                    communityIndex, queryEmbeddingProvider(), 2);

            CommunityReportVectorSearchService.Search search = service.search("alpha question", 1);

            assertEquals("alpha question", search.query());
            assertEquals(1, search.results().size());
            assertEquals("urn:community:alpha", search.results().getFirst().communityUri());
        }
    }

    private static EmbeddingProvider queryEmbeddingProvider() {
        return (text, dimension) -> new float[] { 1.0f, 0.0f };
    }
}