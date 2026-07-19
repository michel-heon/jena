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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Locale;

import org.apache.jena.graphrag.index.ChunkVectorIndexer;
import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.LuceneVectorIndex;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance tests for the PDF ingestion pipeline.
 * <p>
 * <strong>Tranche 3 — RED tests.</strong>  All {@code ingest_*} tests currently
 * fail with {@code UnsupportedOperationException} because
 * {@link DocumentIngestionService#ingest} is a stub.
 * Config-validation tests ({@code config_*}) are GREEN immediately.
 * <p>
 * These tests drive the Tranche 4 implementation (ADR-403).
 */
public class TestPdfDocumentIngestion {

    @TempDir
    Path tempDir;

    private Dataset         dataset;
    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        dataset = DatasetFactory.createTxnMem();
        service = new DocumentIngestionService(DocumentIngestionConfig.defaults());
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void ingest_singlePagePdf_producesOneDocumentAndAtLeastOneChunk() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(
                tempDir, "single.pdf", "Hello from GraphRAG PDF ingestion.");

        service.ingest(pdf, dataset);

        assertEquals(1, countType(GRAG.Document), "expected exactly 1 mg:Document");
        assertTrue(countType(GRAG.Chunk) >= 1,    "expected at least 1 mg:Chunk");
    }

    @Test
    void ingest_chunksLinkedToDocumentViaPartOf() throws Exception {
        Path pdf = PdfTestFixtures.createMultiChunkPdf(tempDir, "multi.pdf");

        service.ingest(pdf, dataset);

        long chunks = countType(GRAG.Chunk);
        assertTrue(chunks >= 2, "multi-chunk PDF must produce at least 2 chunks");
        assertEquals(chunks, countSubjectsWithProperty(GRAG.partOf),
                "every chunk must carry mg:partOf pointing to the document");
    }

    @Test
    void ingest_chunksHaveTextProperty() throws Exception {
        Path pdf = PdfTestFixtures.createMultiChunkPdf(tempDir, "text.pdf");

        service.ingest(pdf, dataset);

        assertEquals(countType(GRAG.Chunk), countSubjectsWithProperty(GRAG.text),
                "every chunk must have mg:text");
    }

    @Test
    void ingest_chunksHaveChunkIndexProperty() throws Exception {
        Path pdf = PdfTestFixtures.createMultiChunkPdf(tempDir, "idx.pdf");

        service.ingest(pdf, dataset);

        assertEquals(countType(GRAG.Chunk), countSubjectsWithProperty(GRAG.chunkIndex),
                "every chunk must have mg:chunkIndex");
    }

    @Test
    void ingest_documentHasSourceHashAndSourceFile() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(
                tempDir, "trace.pdf", "traceable content for GraphRAG");

        service.ingest(pdf, dataset);

        assertEquals(1, countSubjectsWithProperty(GRAG.sourceHash),
                "mg:Document must carry mg:sourceHash");
        assertEquals(1, countSubjectsWithProperty(GRAG.sourceFile),
                "mg:Document must carry mg:sourceFile");
    }

    @Test
    void ingestAndVectorize_pdfChunksAreSearchableByKnn() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(
                tempDir, "vector.pdf", "alpha vectorization signal for GraphRAG");

        try (LuceneVectorIndex vectorIndex = new LuceneVectorIndex(new ByteBuffersDirectory(), 2, VectorSimilarityFunction.EUCLIDEAN)) {
            KeywordEmbeddingProvider provider = new KeywordEmbeddingProvider();
            ChunkVectorizationService vectorizationService = new ChunkVectorizationService(
                    new ChunkVectorIndexer(vectorIndex, provider, 2));

            ChunkVectorizationService.Result result = service.ingestAndVectorize(pdf, dataset, vectorizationService);

            String chunkUri = queryFirstChunkUri(dataset);
            assertTrue(result.chunksSeen() >= 1, "expected vectorization to see at least one chunk");
            assertEquals(result.chunksSeen(), result.chunksIndexed(), "all fresh PDF chunks must be indexed");
            assertEquals(0, result.chunksAlreadyIndexed(), "first vectorization run must not skip fresh chunks");
            assertEquals(result.chunksIndexed(), provider.calls, "provider must be called once per indexed chunk");
            assertTrue(vectorIndex.contains(chunkUri), "PDF chunk URI must be present in vector index");
            assertEquals(chunkUri, vectorIndex.search(new float[] { 1.0f, 0.0f }, 1).getFirst().uri());
        }
    }

    // =========================================================================
    // URI stability
    // =========================================================================

    @Test
    void ingest_sameFileTwiceIndependently_documentUriIsIdentical() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(
                tempDir, "stable.pdf", "stable content for URI determinism");

        Dataset ds1 = DatasetFactory.createTxnMem();
        Dataset ds2 = DatasetFactory.createTxnMem();
        new DocumentIngestionService(DocumentIngestionConfig.defaults()).ingest(pdf, ds1);
        new DocumentIngestionService(DocumentIngestionConfig.defaults()).ingest(pdf, ds2);

        assertEquals(queryFirstDocumentUri(ds1), queryFirstDocumentUri(ds2),
                "document URI must be derived from file content hash — not path or time");
    }

    // =========================================================================
    // Idempotency
    // =========================================================================

    @Test
    void ingest_sameFileTwiceIntoSameDataset_tripleCountUnchanged() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(
                tempDir, "idem.pdf", "idempotency test content for GraphRAG ingestion");

        service.ingest(pdf, dataset);
        long afterFirst = countTriples();

        service.ingest(pdf, dataset);
        long afterSecond = countTriples();

        assertEquals(afterFirst, afterSecond,
                "RDF set semantics must prevent duplicate triples on second ingestion");
    }

    // =========================================================================
    // Error cases — each must leave the dataset empty (no partial write)
    // =========================================================================

    @Test
    void ingest_nonPdfFile_throwsInvalidFormat() throws Exception {
        Path notPdf = PdfTestFixtures.createNonPdfFile(tempDir, "fake.bin");

        IngestionException ex = assertThrows(IngestionException.class,
                () -> service.ingest(notPdf, dataset));
        assertEquals(ErrorKind.INVALID_FORMAT, ex.getKind());
        assertEquals(0, countTriples(), "no triples must be written when format is invalid");
    }

    @Test
    void ingest_encryptedPdf_throwsEncrypted() throws Exception {
        Path encrypted = PdfTestFixtures.createEncryptedPdf(tempDir, "enc.pdf");

        IngestionException ex = assertThrows(IngestionException.class,
                () -> service.ingest(encrypted, dataset));
        assertEquals(ErrorKind.ENCRYPTED, ex.getKind());
        assertEquals(0, countTriples(), "no triples must be written for encrypted PDF");
    }

    @Test
    void ingest_emptyPagePdf_throwsNoText() throws Exception {
        Path empty = PdfTestFixtures.createEmptyPagePdf(tempDir, "empty.pdf");

        IngestionException ex = assertThrows(IngestionException.class,
                () -> service.ingest(empty, dataset));
        assertEquals(ErrorKind.NO_TEXT, ex.getKind());
        assertEquals(0, countTriples(), "no triples must be written when no text is extractable");
    }

    @Test
    void ingest_oversizedFile_throwsFileTooLarge() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(tempDir, "small.pdf", "tiny");
        // maxFileSizeBytes=10 is far below any real PDF — guaranteed to trigger the limit
        DocumentIngestionConfig tinyConfig = new DocumentIngestionConfig(
                DocumentIngestionConfig.DEFAULT_BASE_URI, 50, 10, 10L);

        IngestionException ex = assertThrows(IngestionException.class,
                () -> new DocumentIngestionService(tinyConfig).ingest(pdf, dataset));
        assertEquals(ErrorKind.FILE_TOO_LARGE, ex.getKind());
        assertEquals(0, countTriples(), "no triples must be written when file is too large");
    }

    @Test
    void ingest_nullPath_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.ingest(null, dataset));
    }

    // =========================================================================
    // Config validation — GREEN in Tranche 3
    // =========================================================================

    @Test
    void config_blankBaseUri_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig("", 1_000, 200, 50_000_000L));
    }

    @Test
    void config_nullBaseUri_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig(null, 1_000, 200, 50_000_000L));
    }

    @Test
    void config_chunkSizeBelowMinimum_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig(
                        DocumentIngestionConfig.DEFAULT_BASE_URI,
                        DocumentIngestionConfig.MIN_CHUNK_SIZE - 1,
                        10, 50_000_000L));
    }

    @Test
    void config_chunkOverlapEqualToChunkSize_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig(
                        DocumentIngestionConfig.DEFAULT_BASE_URI, 100, 100, 50_000_000L));
    }

    @Test
    void config_chunkOverlapExceedsChunkSize_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig(
                        DocumentIngestionConfig.DEFAULT_BASE_URI, 100, 101, 50_000_000L));
    }

    @Test
    void config_negativeMaxFileSize_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentIngestionConfig(
                        DocumentIngestionConfig.DEFAULT_BASE_URI, 1_000, 200, -1L));
    }

    @Test
    void config_defaults_areValid() {
        DocumentIngestionConfig cfg = DocumentIngestionConfig.defaults();
        assertEquals(DocumentIngestionConfig.DEFAULT_BASE_URI,       cfg.baseUri());
        assertEquals(DocumentIngestionConfig.DEFAULT_CHUNK_SIZE,     cfg.chunkSize());
        assertEquals(DocumentIngestionConfig.DEFAULT_CHUNK_OVERLAP,  cfg.chunkOverlap());
        assertEquals(DocumentIngestionConfig.DEFAULT_MAX_FILE_SIZE_BYTES, cfg.maxFileSizeBytes());
    }

    @Test
    void config_fromSystemProperties_usesExternalizedValues() {
        String previousBaseUri = setProperty(DocumentIngestionConfig.BASE_URI_PROPERTY, "http://example.test/data#");
        String previousChunkSize = setProperty(DocumentIngestionConfig.CHUNK_SIZE_PROPERTY, "512");
        String previousChunkOverlap = setProperty(DocumentIngestionConfig.CHUNK_OVERLAP_PROPERTY, "64");
        String previousMaxFileSize = setProperty(DocumentIngestionConfig.MAX_FILE_SIZE_BYTES_PROPERTY, "4096");
        try {
            DocumentIngestionConfig cfg = DocumentIngestionConfig.fromSystemProperties();

            assertEquals("http://example.test/data#", cfg.baseUri());
            assertEquals(512, cfg.chunkSize());
            assertEquals(64, cfg.chunkOverlap());
            assertEquals(4_096L, cfg.maxFileSizeBytes());
        } finally {
            restoreProperty(DocumentIngestionConfig.BASE_URI_PROPERTY, previousBaseUri);
            restoreProperty(DocumentIngestionConfig.CHUNK_SIZE_PROPERTY, previousChunkSize);
            restoreProperty(DocumentIngestionConfig.CHUNK_OVERLAP_PROPERTY, previousChunkOverlap);
            restoreProperty(DocumentIngestionConfig.MAX_FILE_SIZE_BYTES_PROPERTY, previousMaxFileSize);
        }
    }

    @Test
    void config_fromSystemProperties_rejectsInvalidExternalizedValues() {
        String previousChunkSize = setProperty(DocumentIngestionConfig.CHUNK_SIZE_PROPERTY, "not-an-int");
        try {
            assertThrows(IllegalArgumentException.class, DocumentIngestionConfig::fromSystemProperties);
        } finally {
            restoreProperty(DocumentIngestionConfig.CHUNK_SIZE_PROPERTY, previousChunkSize);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private long countType(Resource type) {
        Query q = QueryFactory.create(
                "SELECT (COUNT(DISTINCT ?s) AS ?c) WHERE { ?s a <" + type.getURI() + "> }");
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecution.dataset(dataset).query(q).build()) {
            return qe.execSelect().next().getLiteral("c").getLong();
        } finally {
            dataset.end();
        }
    }

    private long countSubjectsWithProperty(Property p) {
        Query q = QueryFactory.create(
                "SELECT (COUNT(DISTINCT ?s) AS ?c) WHERE { ?s <" + p.getURI() + "> ?o }");
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecution.dataset(dataset).query(q).build()) {
            return qe.execSelect().next().getLiteral("c").getLong();
        } finally {
            dataset.end();
        }
    }

    private long countTriples() {
        Query q = QueryFactory.create("SELECT (COUNT(*) AS ?c) WHERE { ?s ?p ?o }");
        dataset.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecution.dataset(dataset).query(q).build()) {
            return qe.execSelect().next().getLiteral("c").getLong();
        } finally {
            dataset.end();
        }
    }

    private String queryFirstDocumentUri(Dataset ds) {
        Query q = QueryFactory.create(
                "SELECT ?s WHERE { ?s a <" + GRAG.Document.getURI() + "> } LIMIT 1");
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecution.dataset(ds).query(q).build()) {
            ResultSet rs = qe.execSelect();
            assertTrue(rs.hasNext(), "expected at least one mg:Document in dataset");
            return rs.next().getResource("s").getURI();
        } finally {
            ds.end();
        }
    }

    private String queryFirstChunkUri(Dataset ds) {
        Query q = QueryFactory.create(
                "SELECT ?s WHERE { ?s a <" + GRAG.Chunk.getURI() + "> } LIMIT 1");
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecution.dataset(ds).query(q).build()) {
            ResultSet rs = qe.execSelect();
            assertTrue(rs.hasNext(), "expected at least one mg:Chunk in dataset");
            return rs.next().getResource("s").getURI();
        } finally {
            ds.end();
        }
    }

    private String setProperty(String property, String value) {
        String previous = System.getProperty(property);
        System.setProperty(property, value);
        return previous;
    }

    private void restoreProperty(String property, String previous) {
        if (previous == null)
            System.clearProperty(property);
        else
            System.setProperty(property, previous);
    }

    private static final class KeywordEmbeddingProvider implements EmbeddingProvider {
        private int calls;

        @Override
        public float[] embed(String text, int dimension) {
            calls++;
            if (dimension != 2)
                throw new IllegalArgumentException("dimension must be 2: " + dimension);
            if (text.toLowerCase(Locale.ROOT).contains("alpha"))
                return new float[] { 1.0f, 0.0f };
            return new float[] { 0.0f, 1.0f };
        }
    }
}
