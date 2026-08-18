/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.tutorial;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.jena.graphrag.ingestion.ChunkExtractionService;
import org.apache.jena.graphrag.ingestion.DocumentIngestionConfig;
import org.apache.jena.graphrag.ingestion.DocumentIngestionService;
import org.apache.jena.graphrag.provider.HttpCommunitySummarizer;
import org.apache.jena.graphrag.provider.HttpEntityExtractor;
import org.apache.jena.graphrag.provider.HttpRelationshipExtractor;
import org.apache.jena.graphrag.provider.ProviderConfiguration;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.XSD;

/** Materializes a configured PDF corpus through the production ingestion service for browser qualification. */
public final class PdfCorpusMaterializer {
    private static final String CHAT_ENDPOINT = "OPENAI_API_URL";
    private static final String CHAT_API_KEY = "OPENAI_API_KEY";
    private static final String CHAT_MODEL = "GRAPHRAG_CHAT_MODEL";

    private PdfCorpusMaterializer() {}

    public static void main(String[] args) throws Exception {
        if ( args.length != 2 && (args.length != 4 || !"--semantic".equals(args[2])) )
            throw new IllegalArgumentException("Usage: PdfCorpusMaterializer <pdf-directory> <output-turtle> [--semantic <checkpoint-file>]");
        Path pdfDirectory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        boolean semantic = args.length == 4;
        List<Path> pdfFiles;
        try ( var files = Files.list(pdfDirectory) ) {
            pdfFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                    .sorted().toList();
        }
        if ( pdfFiles.isEmpty() )
            throw new IllegalArgumentException("Aucun PDF dans le corpus: " + pdfDirectory);

        Dataset dataset = DatasetFactory.createTxnMem();
        AtomicBoolean materialized = new AtomicBoolean();
        if ( semantic )
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if ( materialized.compareAndSet(false, true) ) {
                    write(dataset, output);
                    System.out.println("Extraction interrupted; partial semantic graph saved to " + output + ".");
                }
            }));
        DocumentIngestionConfig config = DocumentIngestionConfig.fromSystemProperties();
        DocumentIngestionService ingestion = new DocumentIngestionService(config);
        for ( int index = 0; index < pdfFiles.size(); index++ ) {
            Path pdfFile = pdfFiles.get(index);
            System.out.printf("Ingesting PDF %d/%d: %s%n", index + 1, pdfFiles.size(), pdfFile.getFileName());
            ingestion.ingest(pdfFile, dataset);
        }

        ChunkExtractionService.Result extractionResult = null;
        if ( semantic ) {
            System.out.println("Starting semantic extraction.");
            extractionResult = extractionService(config).enrich(dataset, Path.of(args[3]), progress ->
                    System.out.printf("%s %d%s: %s%n", progress.phase(), progress.current(),
                        progress.total() == 0 ? "" : "/" + progress.total(),
                        progress.restoredFromCheckpoint() ? "checkpoint" : "provider"));
        }

        dataset.getDefaultModel().setNsPrefix("grag", GRAG.NS);
        dataset.getDefaultModel().setNsPrefix("data", config.baseUri());
        dataset.getDefaultModel().setNsPrefix("xsd", XSD.NS);
        write(dataset, output);
        materialized.set(true);
        if ( extractionResult != null )
            System.out.printf("Semantic extraction: chunks=%d, entities=%d, relationships=%d, communities=%d%n",
                    extractionResult.chunksSeen(), extractionResult.entitiesCreated(),
                    extractionResult.relationshipsCreated(), extractionResult.communitiesCreated());
    }

    private static ChunkExtractionService extractionService(DocumentIngestionConfig config) {
        requireRealProviderEnvironment(System.getenv());
        String endpoint = System.getenv(CHAT_ENDPOINT);
        String apiKey = System.getenv(CHAT_API_KEY);
        String model = System.getenv(CHAT_MODEL);
        ProviderConfiguration providerConfiguration = new ProviderConfiguration(true, Duration.ofSeconds(30), 16_384);
        return new ChunkExtractionService(config.baseUri(),
                new HttpEntityExtractor(providerConfiguration, URI.create(endpoint), model, apiKey),
                new HttpRelationshipExtractor(providerConfiguration, URI.create(endpoint), model, apiKey),
                new HttpCommunitySummarizer(providerConfiguration, URI.create(endpoint), model, apiKey));
    }

    private static void write(Dataset dataset, Path output) {
        try (OutputStream outputStream = Files.newOutputStream(output)) {
            RDFDataMgr.write(outputStream, dataset.getDefaultModel(), Lang.TURTLE);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to write corpus", ex);
        }
    }

    private static void requireRealProviderEnvironment(Map<String, String> environment) {
        List<String> variables = List.of("GRAPHRAG_EMBEDDING_API_URL", "GRAPHRAG_EMBEDDING_API_KEY",
                "GRAPHRAG_EMBEDDING_MODEL", "GRAPHRAG_EMBEDDING_DIMENSION", "OPENAI_API_URL",
                "OPENAI_API_KEY", "GRAPHRAG_CHAT_MODEL");
        List<String> missing = variables.stream()
                .filter(variable -> environment.get(variable) == null || environment.get(variable).isBlank())
                .toList();
        if ( !missing.isEmpty() )
            throw new IllegalStateException("Required real-provider environment variables are not set: "
                    + String.join(", ", missing));
    }
}