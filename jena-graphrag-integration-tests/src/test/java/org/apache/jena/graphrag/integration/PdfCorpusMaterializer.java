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

package org.apache.jena.graphrag.integration;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.jena.graphrag.ingestion.DocumentIngestionConfig;
import org.apache.jena.graphrag.ingestion.DocumentIngestionService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/** Materializes a configured PDF corpus through the production ingestion service for browser qualification. */
public final class PdfCorpusMaterializer {
    private PdfCorpusMaterializer() {}

    public static void main(String[] args) throws Exception {
        if ( args.length != 2 )
            throw new IllegalArgumentException("Usage: PdfCorpusMaterializer <pdf-directory> <output-turtle>");
        Path pdfDirectory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        List<Path> pdfFiles;
        try ( var files = Files.list(pdfDirectory) ) {
            pdfFiles = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                    .sorted().toList();
        }
        if ( pdfFiles.isEmpty() )
            throw new IllegalArgumentException("Aucun PDF dans le corpus: " + pdfDirectory);

        Dataset dataset = DatasetFactory.createTxnMem();
        DocumentIngestionService ingestion = new DocumentIngestionService(DocumentIngestionConfig.fromSystemProperties());
        for ( Path pdfFile : pdfFiles )
            ingestion.ingest(pdfFile, dataset);

        try (OutputStream outputStream = Files.newOutputStream(output)) {
            RDFDataMgr.write(outputStream, dataset.getDefaultModel(), Lang.TURTLE);
        }
    }
}