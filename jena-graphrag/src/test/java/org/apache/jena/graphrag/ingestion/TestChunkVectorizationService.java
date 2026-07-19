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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graphrag.index.ChunkVectorIndexer;
import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.graphrag.index.VectorResult;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestChunkVectorizationService {

    @Test
    public void vectorize_indexesTextualChunksAndReportsAlreadyIndexedChunks() {
        Dataset dataset = DatasetFactory.createTxnMem();
        addChunk(dataset, "http://example.test/chunk-1", "alpha acquisition signal");
        addChunk(dataset, "http://example.test/chunk-2", "beta relationship signal");

        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            vectorIndex.index("http://example.test/chunk-2", new float[] { 0.0f, 1.0f });
            CountingEmbeddingProvider provider = new CountingEmbeddingProvider();
            ChunkVectorizationService service = new ChunkVectorizationService(
                    new ChunkVectorIndexer(vectorIndex, provider, 2));

            ChunkVectorizationService.Result result = service.vectorize(dataset);

            assertEquals(2, result.chunksSeen());
            assertEquals(1, result.chunksIndexed());
            assertEquals(1, result.chunksAlreadyIndexed());
            assertEquals(1, provider.calls);
            assertTrue(vectorIndex.contains("http://example.test/chunk-1"));
            VectorResult nearest = vectorIndex.search(new float[] { 1.0f, 0.0f }, 1).getFirst();
            assertEquals("http://example.test/chunk-1", nearest.uri());
        }
    }

    @Test
    public void vectorize_replaySkipsPreviouslyIndexedSyntheticCorpus() {
        Dataset dataset = DatasetFactory.createTxnMem();
        for (int chunkIndex = 0; chunkIndex < 50; chunkIndex++)
            addChunk(dataset, "http://example.test/chunk-" + chunkIndex, "synthetic chunk " + chunkIndex);

        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            CountingEmbeddingProvider provider = new CountingEmbeddingProvider();
            ChunkVectorizationService service = new ChunkVectorizationService(
                    new ChunkVectorIndexer(vectorIndex, provider, 2));

            ChunkVectorizationService.Result first = service.vectorize(dataset);
            ChunkVectorizationService.Result second = service.vectorize(dataset);

            assertEquals(50, first.chunksSeen());
            assertEquals(50, first.chunksIndexed());
            assertEquals(0, first.chunksAlreadyIndexed());
            assertEquals(50, second.chunksSeen());
            assertEquals(0, second.chunksIndexed());
            assertEquals(50, second.chunksAlreadyIndexed());
            assertEquals(50, provider.calls);
            for (int chunkIndex = 0; chunkIndex < 50; chunkIndex++)
                assertTrue(vectorIndex.contains("http://example.test/chunk-" + chunkIndex));
        }
    }

    private static void addChunk(Dataset dataset, String uri, String text) {
        dataset.begin(ReadWrite.WRITE);
        try {
            Model model = dataset.getDefaultModel();
            model.createResource(uri)
                    .addProperty(RDF.type, GRAG.Chunk)
                    .addLiteral(GRAG.text, text);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    private static final class CountingEmbeddingProvider implements EmbeddingProvider {
        private int calls;

        @Override
        public float[] embed(String text, int dimension) {
            calls++;
            if (text.contains("alpha"))
                return new float[] { 1.0f, 0.0f };
            return new float[] { 0.0f, 1.0f };
        }
    }
}