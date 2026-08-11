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

package org.apache.jena.graphrag.ingestion;

import java.util.List;
import java.util.Objects;

import org.apache.jena.graphrag.index.ChunkVectorIndexer;
import org.apache.jena.query.Dataset;

/** Vectorizes existing {@code mg:Chunk} resources through a {@link ChunkVectorIndexer}. */
public final class ChunkVectorizationService {

    private final ChunkVectorIndexer chunkVectorIndexer;

    public ChunkVectorizationService(ChunkVectorIndexer chunkVectorIndexer) {
        this.chunkVectorIndexer = Objects.requireNonNull(chunkVectorIndexer, "chunkVectorIndexer");
    }

    public Result vectorize(Dataset dataset) {
        Objects.requireNonNull(dataset, "dataset");

        List<ChunkTextReader.ChunkText> chunks = ChunkTextReader.read(dataset);
        int indexed = 0;
        int alreadyIndexed = 0;
        for (ChunkTextReader.ChunkText chunk : chunks) {
            if (chunkVectorIndexer.indexChunk(chunk.uri(), chunk.text()))
                indexed++;
            else
                alreadyIndexed++;
        }
        return new Result(chunks.size(), indexed, alreadyIndexed);
    }

    public record Result(int chunksSeen, int chunksIndexed, int chunksAlreadyIndexed) {}
}