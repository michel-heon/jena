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

import java.util.List;
import java.util.Objects;

import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.VectorIndex;

/** Vector-only retrieval of community reports, keyed by {@code mg:Community} URI. */
public final class CommunityReportVectorSearchService {

    private final VectorIndex communityVectorIndex;
    private final EmbeddingProvider embeddingProvider;
    private final int dimension;

    public CommunityReportVectorSearchService(VectorIndex communityVectorIndex, EmbeddingProvider embeddingProvider,
                                              int dimension) {
        this.communityVectorIndex = Objects.requireNonNull(communityVectorIndex, "communityVectorIndex");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        if ( dimension < 1 )
            throw new IllegalArgumentException("dimension must be greater than zero");
        this.dimension = dimension;
    }

    public Search search(String query, int topK) {
        if ( query == null || query.isBlank() )
            throw new IllegalArgumentException("parametre 'q' requis");
        if ( topK < 1 )
            throw new IllegalArgumentException("topK must be greater than zero");

        float[] queryVector = Objects.requireNonNull(embeddingProvider.embed(query, dimension), "query vector");
        if ( queryVector.length != dimension )
            throw new IllegalArgumentException("query vector dimension must be " + dimension + ": " + queryVector.length);
        List<Result> results = communityVectorIndex.search(queryVector, topK).stream()
                .map(result -> new Result(result.uri(), result.score()))
                .toList();
        return new Search(query, results);
    }

    public record Search(String query, List<Result> results) {}

    public record Result(String communityUri, float score) {}
}