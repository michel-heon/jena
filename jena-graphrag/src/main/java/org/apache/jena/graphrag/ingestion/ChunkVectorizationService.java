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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.graphrag.index.ChunkVectorIndexer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;

/** Vectorizes existing {@code mg:Chunk} resources through a {@link ChunkVectorIndexer}. */
public final class ChunkVectorizationService {

    private final ChunkVectorIndexer chunkVectorIndexer;

    public ChunkVectorizationService(ChunkVectorIndexer chunkVectorIndexer) {
        this.chunkVectorIndexer = Objects.requireNonNull(chunkVectorIndexer, "chunkVectorIndexer");
    }

    public Result vectorize(Dataset dataset) {
        Objects.requireNonNull(dataset, "dataset");

        List<ChunkText> chunks = readChunkTexts(dataset);
        int indexed = 0;
        int alreadyIndexed = 0;
        for (ChunkText chunk : chunks) {
            if (chunkVectorIndexer.indexChunk(chunk.uri(), chunk.text()))
                indexed++;
            else
                alreadyIndexed++;
        }
        return new Result(chunks.size(), indexed, alreadyIndexed);
    }

    private static List<ChunkText> readChunkTexts(Dataset dataset) {
        Query query = QueryFactory.create("""
                SELECT DISTINCT ?chunk ?text WHERE {
                  ?chunk a <%s> ;
                         <%s> ?text .
                  FILTER(isIRI(?chunk))
                  FILTER(isLiteral(?text) && STRLEN(STR(?text)) > 0)
                }
                ORDER BY STR(?chunk)
                """.formatted(GRAG.Chunk.getURI(), GRAG.text.getURI()));

        List<ChunkText> chunks = new ArrayList<>();
        dataset.begin(ReadWrite.READ);
        try (QueryExecution queryExecution = QueryExecution.dataset(dataset).query(query).build()) {
            queryExecution.execSelect().forEachRemaining(solution -> chunks.add(toChunkText(solution)));
        } finally {
            dataset.end();
        }
        return List.copyOf(chunks);
    }

    private static ChunkText toChunkText(QuerySolution solution) {
        Resource chunk = solution.getResource("chunk");
        return new ChunkText(chunk.getURI(), solution.getLiteral("text").getString());
    }

    public record Result(int chunksSeen, int chunksIndexed, int chunksAlreadyIndexed) {}

    private record ChunkText(String uri, String text) {}
}