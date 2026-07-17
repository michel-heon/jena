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
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Factory for minimal PDF fixtures used in unit tests.
 * All PDFs are generated programmatically by PDFBox — no external file,
 * no licence concerns.
 * <p>
 * Content is deterministic so hash-stability assertions remain valid
 * across JVM invocations.
 */
public final class PdfTestFixtures {

    private PdfTestFixtures() {}

    /**
     * Creates a single-page PDF containing {@code text} in Helvetica 12pt.
     * The text is ASCII-safe and short enough to fit on one line.
     */
    public static Path createPlainTextPdf(Path dir, String filename, String text)
            throws IOException {
        Path path = dir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.beginText();
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * Creates a multi-page PDF with enough text to produce at least 2 chunks
     * using the default {@link DocumentIngestionConfig#DEFAULT_CHUNK_SIZE} of 1 000
     * characters and {@link DocumentIngestionConfig#DEFAULT_CHUNK_OVERLAP} of 200.
     * <p>
     * 3 pages × 20 lines × ~48 chars/line ≈ 2 880 chars of extracted text.
     */
    public static Path createMultiChunkPdf(Path dir, String filename) throws IOException {
        Path path = dir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            for (int p = 0; p < 3; p++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.beginText();
                    cs.newLineAtOffset(50, 700);
                    for (int i = 0; i < 20; i++) {
                        cs.showText("GraphRAG PDF ingestion test line " + (p * 20 + i + 1) + ".");
                        cs.newLineAtOffset(0, -15);
                    }
                    cs.endText();
                }
            }
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * Creates a PDF whose single page has no content stream — PDFTextStripper
     * will return empty text, triggering {@link ErrorKind#NO_TEXT}.
     */
    public static Path createEmptyPagePdf(Path dir, String filename) throws IOException {
        Path path = dir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage()); // no content stream → no extractable text
            doc.save(path.toFile());
        }
        return path;
    }

    /**
     * Creates a file whose first bytes are NOT {@code %PDF-}, so magic-byte
     * validation rejects it as {@link ErrorKind#INVALID_FORMAT}.
     */
    public static Path createNonPdfFile(Path dir, String filename) throws IOException {
        Path path = dir.resolve(filename);
        Files.writeString(path, "This is not a PDF. No magic bytes here.");
        return path;
    }

    /**
     * Creates a password-protected PDF.  Opening it without the user password
     * causes PDFBox to throw {@code InvalidPasswordException}, which the service
     * maps to {@link ErrorKind#ENCRYPTED}.
     */
    public static Path createEncryptedPdf(Path dir, String filename) throws IOException {
        Path path = dir.resolve(filename);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(false);
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("ownerSecret", "userSecret", ap);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(path.toFile());
        }
        return path;
    }
}
