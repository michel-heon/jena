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

import java.util.Objects;

/** Indexes {@code mg:Chunk} text embeddings into a {@link VectorIndex}. */
public final class ChunkVectorIndexer {

    private final VectorIndex vectorIndex;
    private final EmbeddingProvider embeddingProvider;
    private final int dimension;

    public ChunkVectorIndexer(VectorIndex vectorIndex, EmbeddingProvider embeddingProvider, int dimension) {
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        if ( dimension < 1 )
            throw new IllegalArgumentException("dimension must be greater than zero");
        this.dimension = dimension;
    }

    public boolean indexChunk(String chunkUri, String text) {
        if ( chunkUri == null || chunkUri.isBlank() )
            throw new IllegalArgumentException("chunkUri must not be blank");
        if ( text == null || text.isBlank() )
            throw new IllegalArgumentException("text must not be blank");
        if ( vectorIndex.contains(chunkUri) )
            return false;

        float[] vector = Objects.requireNonNull(embeddingProvider.embed(text, dimension), "embedding vector");
        if ( vector.length != dimension )
            throw new IllegalArgumentException("embedding dimension must be " + dimension + ": " + vector.length);
        vectorIndex.index(chunkUri, vector);
        return true;
    }
}