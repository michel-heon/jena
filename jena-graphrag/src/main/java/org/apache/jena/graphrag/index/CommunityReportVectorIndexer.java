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

import java.util.Objects;

/** Indexes normalized {@code mg:Community} report content into a {@link VectorIndex}. */
public final class CommunityReportVectorIndexer {

    private final VectorIndex vectorIndex;
    private final EmbeddingProvider embeddingProvider;
    private final int dimension;

    public CommunityReportVectorIndexer(VectorIndex vectorIndex, EmbeddingProvider embeddingProvider, int dimension) {
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        if ( dimension < 1 )
            throw new IllegalArgumentException("dimension must be greater than zero");
        this.dimension = dimension;
    }

    public boolean indexCommunity(String communityUri, String summary, String fullContent) {
        if ( communityUri == null || communityUri.isBlank() )
            throw new IllegalArgumentException("communityUri must not be blank");
        String report = reportText(summary, fullContent);
        if ( vectorIndex.contains(communityUri) )
            return false;

        float[] vector = Objects.requireNonNull(embeddingProvider.embed(report, dimension), "embedding vector");
        if ( vector.length != dimension )
            throw new IllegalArgumentException("embedding dimension must be " + dimension + ": " + vector.length);
        vectorIndex.index(communityUri, vector);
        return true;
    }

    static String reportText(String summary, String fullContent) {
        String normalizedSummary = normalize(summary);
        String normalizedFullContent = normalize(fullContent);
        if ( normalizedSummary.isEmpty() && normalizedFullContent.isEmpty() )
            throw new IllegalArgumentException("community report must contain a summary or full content");
        if ( normalizedSummary.isEmpty() )
            return normalizedFullContent;
        if ( normalizedFullContent.isEmpty() || normalizedSummary.equals(normalizedFullContent) )
            return normalizedSummary;
        return normalizedSummary + "\n\n" + normalizedFullContent;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}