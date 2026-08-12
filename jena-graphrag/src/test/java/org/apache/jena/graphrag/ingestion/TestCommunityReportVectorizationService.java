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

package org.apache.jena.graphrag.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graphrag.index.CommunityReportVectorIndexer;
import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestCommunityReportVectorizationService {

    @Test
    public void vectorize_indexesCommunityReportsAndSkipsEmptyAndPreviouslyIndexedReports() {
        Dataset dataset = DatasetFactory.createTxnMem();
        addCommunity(dataset, "urn:community:alpha", "alpha summary", "alpha full content");
        addCommunity(dataset, "urn:community:beta", "beta summary", null);
        addCommunity(dataset, "urn:community:empty", null, null);

        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            CountingEmbeddingProvider provider = new CountingEmbeddingProvider();
            CommunityReportVectorizationService service = new CommunityReportVectorizationService(
                    new CommunityReportVectorIndexer(vectorIndex, provider, 2));

            CommunityReportVectorizationService.Result first = service.vectorize(dataset);
            CommunityReportVectorizationService.Result second = service.vectorize(dataset);

            assertEquals(3, first.communitiesSeen());
            assertEquals(2, first.communitiesIndexed());
            assertEquals(0, first.communitiesAlreadyIndexed());
            assertEquals(1, first.communitiesWithoutReport());
            assertEquals(3, second.communitiesSeen());
            assertEquals(0, second.communitiesIndexed());
            assertEquals(2, second.communitiesAlreadyIndexed());
            assertEquals(1, second.communitiesWithoutReport());
            assertEquals(2, provider.calls);
            assertTrue(vectorIndex.contains("urn:community:alpha"));
            assertTrue(vectorIndex.contains("urn:community:beta"));
        }
    }

    private static void addCommunity(Dataset dataset, String uri, String summary, String fullContent) {
        dataset.begin(ReadWrite.WRITE);
        try {
            Model model = dataset.getDefaultModel();
            var community = model.createResource(uri).addProperty(RDF.type, GRAG.Community);
            if ( summary != null )
                community.addLiteral(GRAG.summary, summary);
            if ( fullContent != null )
                community.addLiteral(GRAG.fullContent, fullContent);
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
            return text.startsWith("alpha") ? new float[] { 1.0f, 0.0f } : new float[] { 0.0f, 1.0f };
        }
    }
}