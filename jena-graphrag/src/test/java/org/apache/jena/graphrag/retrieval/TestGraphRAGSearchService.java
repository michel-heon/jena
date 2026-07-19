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

package org.apache.jena.graphrag.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGSearchService {

    @Test
    public void search_returnsTextVectorAndHybridScores() {
        Dataset dataset = indexedDataset();
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            GraphRAGSearchService service = new GraphRAGSearchService(vectorIndex, new QueryEmbeddingProvider(), 2);

            GraphRAGSearch search = service.search(dataset.asDatasetGraph(), "alpha", 3, 0.5);

            assertEquals("alpha", search.query());
            assertEquals(3, search.results().size());
            GraphRAGSearch.Result first = search.results().getFirst();
            assertEquals(first.scoreHybrid(), 0.5 * first.scoreText() + 0.5 * first.scoreVector(), 0.0001);
            assertTrue(search.results().stream().allMatch(result -> result.scoreText() >= 0.0));
            assertTrue(search.results().stream().allMatch(result -> result.scoreVector() >= 0.0));
        }
    }

    @Test
    public void search_alphaOneKeepsTextScoresAsHybridScores() {
        Dataset dataset = indexedDataset();
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            GraphRAGSearchService service = new GraphRAGSearchService(vectorIndex, new QueryEmbeddingProvider(), 2);

            GraphRAGSearch search = service.search(dataset.asDatasetGraph(), "alpha", 3, 1.0);

            assertTrue(search.results().stream()
                    .allMatch(result -> Math.abs(result.scoreHybrid() - result.scoreText()) < 0.0001));
        }
    }

    @Test
    public void search_alphaZeroKeepsVectorScoresAsHybridScores() {
        Dataset dataset = indexedDataset();
        try (LuceneVectorIndex vectorIndex = vectorIndex()) {
            GraphRAGSearchService service = new GraphRAGSearchService(vectorIndex, new QueryEmbeddingProvider(), 2);

            GraphRAGSearch search = service.search(dataset.asDatasetGraph(), "alpha", 3, 0.0);

            assertTrue(search.results().stream()
                    .allMatch(result -> Math.abs(result.scoreHybrid() - result.scoreVector()) < 0.0001));
        }
    }

    private static Dataset indexedDataset() {
        Dataset base = DatasetFactory.createTxnMem();
        Dataset dataset = GraphRAGTextDatasetFactory.createChunkTextDataset(base, new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            addChunk(dataset, "urn:chunk:text", "alpha exact lexical match");
            addChunk(dataset, "urn:chunk:both", "alpha vector lexical overlap");
            addChunk(dataset, "urn:chunk:vector", "semantic vector neighbor");
            dataset.commit();
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static LuceneVectorIndex vectorIndex() {
        LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN);
        vectorIndex.index("urn:chunk:text", new float[] { 0.0f, 1.0f });
        vectorIndex.index("urn:chunk:both", new float[] { 0.8f, 0.2f });
        vectorIndex.index("urn:chunk:vector", new float[] { 1.0f, 0.0f });
        return vectorIndex;
    }

    private static void addChunk(Dataset dataset, String uri, String text) {
        Resource chunk = dataset.getDefaultModel().createResource(uri);
        chunk.addProperty(RDF.type, GRAG.Chunk)
             .addProperty(GRAG.text, text);
    }

    private static final class QueryEmbeddingProvider implements EmbeddingProvider {
        @Override
        public float[] embed(String text, int dimension) {
            return new float[] { 1.0f, 0.0f };
        }
    }
}