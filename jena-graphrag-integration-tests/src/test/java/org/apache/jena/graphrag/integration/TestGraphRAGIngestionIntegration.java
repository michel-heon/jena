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

package org.apache.jena.graphrag.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.graphrag.ingestion.ChunkExtractionService;
import org.apache.jena.graphrag.ingestion.DocumentIngestionConfig;
import org.apache.jena.graphrag.ingestion.DocumentIngestionService;
import org.apache.jena.graphrag.retrieval.GraphRAGContext;
import org.apache.jena.graphrag.retrieval.GraphRAGContextService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGraphRAGIngestionIntegration {
    private static final String CORPUS_ROOT = "corpus/";

    @TempDir
    Path temporaryDirectory;

    @Test
    public void rdfImport_normalizesRelationshipsAndIsIdempotent() {
        Dataset dataset = DatasetFactory.createTxnMem();
        Path source = resourcePath("ingestion/graphrag-import.ttl");

        GraphRAGImporter.load(source, dataset);
        long firstSize = datasetSize(dataset);
        GraphRAGImporter.load(source, dataset);

        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getDefaultModel();
            assertEquals(firstSize, model.size());
                assertTrue(model.containsResource(model.createResource(
                    "https://jena.apache.org/graphrag/integration/corpus/alpha")));
            assertTrue(model.contains(null, RDF.type, GRAG.Document));
            assertTrue(model.contains(null, RDF.type, GRAG.Chunk));
            assertTrue(model.contains(null, GRAG.partOf));
            assertTrue(model.contains(null, GRAG.hasEntity));
                assertTrue(!model.contains(null, model.createProperty(GRAG.uri + "has_entity")));
            assertTrue(model.contains(null, RDF.type, GRAG.Relationship));
            assertTrue(model.contains(null, GRAG.source));
            assertTrue(model.contains(null, GRAG.target));
            assertTrue(model.contains(null, GRAG.relatedTo));
            assertTrue(model.contains(null, model.createProperty("http://purl.org/dc/terms/source")));
        } finally {
            dataset.end();
        }
    }

    @Test
    public void rdfImport_invalidCorpusLeavesDatasetUntouched() {
        Dataset dataset = DatasetFactory.createTxnMem();

        org.junit.jupiter.api.Assertions.assertThrows(RiotException.class,
                () -> GraphRAGImporter.load(resourcePath("invalid/not-turtle.ttl"), dataset));

        assertEquals(0, datasetSize(dataset));
    }

    @Test
    public void pdfIngestion_createsTraceableDocumentAndChunks() throws IOException {
        Dataset dataset = GraphRAGTextDatasetFactory.createChunkTextDataset(
            DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        Path pdf = copyResource("ingestion/pdf/reecriture-microsoft-graphrag-rdf-knowledge-graph-part-3.pdf");

        new DocumentIngestionService(DocumentIngestionConfig.defaults()).ingest(pdf, dataset);

        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getDefaultModel();
            assertEquals(1, model.listResourcesWithProperty(RDF.type, GRAG.Document).toList().size());
            assertTrue(model.listResourcesWithProperty(RDF.type, GRAG.Chunk).hasNext());
            assertTrue(model.contains(null, GRAG.partOf));
            assertTrue(model.contains(null, GRAG.text));
            assertTrue(model.contains(null, GRAG.chunkIndex));
            assertTrue(model.contains(null, GRAG.chunkPages));
            assertTrue(model.contains(null, GRAG.sourceHash));
            assertTrue(model.contains(null, GRAG.sourceFile, pdf.getFileName().toString()));
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getDefaultModel();
            var chunk = model.listResourcesWithProperty(RDF.type, GRAG.Chunk).next();
            String text = chunk.getProperty(GRAG.text).getString();
            assertTrue(!text.isBlank());
            String query = text.split("[^A-Za-z]{2,}")[0];
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), GraphRAGContextService.BASIC_MODE, query, 5);

            assertTrue(context.results().stream()
                    .anyMatch(result -> chunk.getURI().equals(result.chunkUri())));
        } finally {
            dataset.end();
        }
    }

    @Test
    public void pdfCorpus_enrichmentCreatesSemanticGraph() throws IOException {
        Dataset dataset = DatasetFactory.createTxnMem();
        Path pdf = copyResource("ingestion/pdf/reecriture-microsoft-graphrag-rdf-knowledge-graph-part-3.pdf");
        ChunkExtractionService enrichment = new ChunkExtractionService("https://jena.apache.org/graphrag/integration/",
                text -> List.of("GraphRAG", "Jena"),
                (text, entities) -> List.of(new org.apache.jena.graphrag.provider.RelationshipExtractor.Relationship(
                        "GraphRAG", "Jena", "documents")),
                (community, findings) -> "GraphRAG and Jena: " + String.join(", ", findings));

        ChunkExtractionService.Result result = new DocumentIngestionService(DocumentIngestionConfig.defaults())
                .ingestAndExtract(pdf, dataset, enrichment);

        assertTrue(result.chunksSeen() > 0);
        assertEquals(2, result.entitiesCreated());
        assertEquals(result.chunksSeen(), result.relationshipsCreated());
        assertEquals(1, result.communitiesCreated());
        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getDefaultModel();
            assertTrue(model.contains(null, RDF.type, GRAG.Entity));
            assertTrue(model.contains(null, RDF.type, GRAG.Relationship));
            assertTrue(model.contains(null, RDF.type, GRAG.Community));
            assertTrue(model.contains(null, GRAG.hasEntity));
            assertTrue(model.contains(null, GRAG.relatedTo));
            assertTrue(model.contains(null, GRAG.inCommunity));
        } finally {
            dataset.end();
        }
    }

    private Path resourcePath(String relativePath) {
        try {
            return Path.of(TestGraphRAGIngestionIntegration.class.getClassLoader()
                    .getResource(CORPUS_ROOT + relativePath).toURI());
        } catch (Exception ex) {
            throw new IllegalStateException("Missing corpus resource: " + relativePath, ex);
        }
    }

    private Path copyResource(String relativePath) throws IOException {
        Path destination = temporaryDirectory.resolve(Path.of(relativePath).getFileName().toString());
        try (InputStream input = TestGraphRAGIngestionIntegration.class.getClassLoader()
                .getResourceAsStream(CORPUS_ROOT + relativePath)) {
            if ( input == null )
                throw new IllegalStateException("Missing corpus resource: " + relativePath);
            Files.copy(input, destination);
        }
        return destination;
    }

    private static long datasetSize(Dataset dataset) {
        dataset.begin(ReadWrite.READ);
        try {
            return dataset.getDefaultModel().size();
        } finally {
            dataset.end();
        }
    }
}