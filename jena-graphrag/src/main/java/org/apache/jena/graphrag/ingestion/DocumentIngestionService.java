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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;

/**
 * Orchestrates PDF ingestion: validate, extract, chunk, then write transactionally.
 */
public class DocumentIngestionService {

    private final DocumentIngestionConfig config;
    private final PdfTextExtractor extractor;
    private final TextChunker chunker;

    public DocumentIngestionService(DocumentIngestionConfig config) {
        if (config == null)
            throw new IllegalArgumentException("config must not be null");
        this.config = config;
        this.extractor = new PdfTextExtractor();
        this.chunker = new TextChunker(config);
    }

    /**
     * Ingests a PDF file into the given {@code Dataset}, producing
     * one {@code mg:Document} and N {@code mg:Chunk} resources.
     *
     * @param pdfPath path to the PDF file (must be readable)
     * @param dataset target Jena dataset (must support transactions)
     * @throws IllegalArgumentException if {@code pdfPath} is null or not readable
     * @throws IngestionException       if the file fails validation or RDF write fails
     */
    public void ingest(Path pdfPath, Dataset dataset) {
        if (pdfPath == null)
            throw new IllegalArgumentException("pdfPath must not be null");
        if (dataset == null)
            throw new IllegalArgumentException("dataset must not be null");

        PdfTextExtractor.ExtractedText extractedText = extractor.extract(pdfPath, config);
        List<TextChunker.TextChunk> chunks = chunker.chunk(extractedText);
        if (chunks.isEmpty())
            throw new IngestionException(ErrorKind.NO_TEXT, "PDF produced no chunks: " + pdfPath);

        String sourceHash = sha256Hex(pdfPath);
        Model additions = toModel(pdfPath, sourceHash, chunks);
        writeAtomically(dataset, additions);
    }

    private Model toModel(Path pdfPath, String sourceHash, List<TextChunker.TextChunk> chunks) {
        Model model = ModelFactory.createDefaultModel();
        String hashPrefix = sourceHash.substring(0, 32);
        Resource document = model.createResource(config.baseUri() + "doc-" + hashPrefix)
                .addProperty(RDF.type, GRAG.Document)
                .addLiteral(GRAG.sourceHash, sourceHash)
                .addLiteral(GRAG.sourceFile, pdfPath.getFileName().toString());

        for (TextChunker.TextChunk chunk : chunks) {
            model.createResource(config.baseUri() + "chunk-" + hashPrefix + "-" + chunk.index())
                    .addProperty(RDF.type, GRAG.Chunk)
                    .addProperty(GRAG.partOf, document)
                    .addLiteral(GRAG.chunkIndex, chunk.index())
                    .addLiteral(GRAG.chunkPages, pageRange(chunk))
                    .addLiteral(GRAG.text, chunk.text());
        }
        return model;
    }

    private static String pageRange(TextChunker.TextChunk chunk) {
        if (chunk.startPage() == chunk.endPage())
            return Integer.toString(chunk.startPage());
        return chunk.startPage() + "-" + chunk.endPage();
    }

    private static void writeAtomically(Dataset dataset, Model additions) {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.getDefaultModel().add(additions);
            dataset.commit();
        } catch (RuntimeException ex) {
            dataset.abort();
            throw new IngestionException(ErrorKind.TRANSACTION_FAILED,
                    "Failed to write PDF ingestion triples", ex);
        } finally {
            dataset.end();
        }
    }

    private static String sha256Hex(Path pdfPath) {
        try (InputStream in = java.nio.file.Files.newInputStream(pdfPath)) {
            return DigestUtils.sha256Hex(in);
        } catch (IOException ex) {
            throw new IngestionException(ErrorKind.INVALID_FORMAT,
                    "Could not hash PDF bytes: " + pdfPath, ex);
        }
    }
}
