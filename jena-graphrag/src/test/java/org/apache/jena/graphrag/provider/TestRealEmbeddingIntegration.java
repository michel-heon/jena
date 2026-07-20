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

package org.apache.jena.graphrag.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.jena.graphrag.index.ChunkVectorIndexer;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.graphrag.ingestion.ChunkVectorizationService;
import org.apache.jena.graphrag.ingestion.DocumentIngestionConfig;
import org.apache.jena.graphrag.ingestion.DocumentIngestionService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.GRAG;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestRealEmbeddingIntegration {

    @Test
    public void realPdf_isIngestedEmbeddedIndexedAndRetrieved() {
        String apiUrl = System.getenv("GRAPHRAG_EMBEDDING_API_URL");
        String apiKey = System.getenv("GRAPHRAG_EMBEDDING_API_KEY");
        String pdf = System.getenv("GRAPHRAG_REAL_PDF");
        Assumptions.assumeTrue(notBlank(apiUrl) && notBlank(apiKey) && notBlank(pdf),
                "real embedding environment is not configured");

        String model = environmentOrDefault("GRAPHRAG_EMBEDDING_MODEL", "text-embedding-3-small");
        int dimension = Integer.parseInt(environmentOrDefault("GRAPHRAG_EMBEDDING_DIMENSION", "1024"));
        Dataset dataset = DatasetFactory.createTxnMem();
        new DocumentIngestionService(DocumentIngestionConfig.fromSystemProperties()).ingest(Path.of(pdf), dataset);
        long chunks = chunkCount(dataset);

        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(
                new ProviderConfiguration(true, Duration.ofSeconds(60), 4096), URI.create(apiUrl), model, apiKey);
        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), dimension,
                VectorSimilarityFunction.EUCLIDEAN)) {
            ChunkVectorizationService.Result result = new ChunkVectorizationService(
                    new ChunkVectorIndexer(vectorIndex, provider, dimension)).vectorize(dataset);
            String firstText = firstChunkText(dataset);
            String firstUri = firstChunkUri(dataset);

            assertEquals(chunks, result.chunksIndexed());
            assertEquals(firstUri, vectorIndex.search(provider.embed(firstText, dimension), 1).getFirst().uri());
            System.out.println("realEmbeddingProvider=" + model);
            System.out.println("realEmbeddingChunks=" + chunks);
            System.out.println("realEmbeddingsCreated=" + result.chunksIndexed());
            System.out.println("realEmbeddingSecretLogged=false");
        }
    }

    private static long chunkCount(Dataset dataset) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution execution = QueryExecution.dataset(dataset).query(
                "SELECT (COUNT(?chunk) AS ?count) WHERE { ?chunk a <" + GRAG.Chunk.getURI() + "> }").build()) {
            return execution.execSelect().next().getLiteral("count").getLong();
        } finally {
            dataset.end();
        }
    }

    private static String firstChunkUri(Dataset dataset) {
        return firstChunkValue(dataset, "?chunk", false);
    }

    private static String firstChunkText(Dataset dataset) {
        return firstChunkValue(dataset, "?text", true);
    }

    private static String firstChunkValue(Dataset dataset, String variable, boolean literal) {
        dataset.begin(ReadWrite.READ);
        try (QueryExecution execution = QueryExecution.dataset(dataset).query(
                "SELECT ?chunk ?text WHERE { ?chunk a <" + GRAG.Chunk.getURI() + "> ; <" + GRAG.text.getURI()
                        + "> ?text } ORDER BY ?chunk LIMIT 1").build()) {
            var solution = execution.execSelect().next();
            return literal ? solution.getLiteral(variable.substring(1)).getString()
                    : solution.getResource(variable.substring(1)).getURI();
        } finally {
            dataset.end();
        }
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return notBlank(value) ? value : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}