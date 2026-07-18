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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Local PDF text extraction adapter for PDFBox 3.x.
 */
final class PdfTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    ExtractedText extract(Path pdfPath, DocumentIngestionConfig config) {
        validateFile(pdfPath, config);

        try (RandomAccessReadBufferedFile source = new RandomAccessReadBufferedFile(pdfPath.toFile());
             PDDocument document = Loader.loadPDF(source)) {
            if (document.isEncrypted())
                throw new IngestionException(ErrorKind.ENCRYPTED, "PDF is encrypted: " + pdfPath);

            ExtractedText extractedText = extractPages(document);
            if (extractedText.text().isBlank())
                throw new IngestionException(ErrorKind.NO_TEXT, "PDF has no extractable text: " + pdfPath);
            return extractedText;
        } catch (InvalidPasswordException ex) {
            throw new IngestionException(ErrorKind.ENCRYPTED, "PDF is encrypted: " + pdfPath, ex);
        } catch (IngestionException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IngestionException(ErrorKind.INVALID_FORMAT, "PDF could not be parsed: " + pdfPath, ex);
        }
    }

    private static void validateFile(Path pdfPath, DocumentIngestionConfig config) {
        if (pdfPath == null)
            throw new IllegalArgumentException("pdfPath must not be null");
        if (!Files.isRegularFile(pdfPath) || !Files.isReadable(pdfPath))
            throw new IllegalArgumentException("pdfPath must be a readable regular file: " + pdfPath);

        long size;
        try {
            size = Files.size(pdfPath);
        } catch (IOException ex) {
            throw new IllegalArgumentException("could not read file size: " + pdfPath, ex);
        }
        if (size > config.maxFileSizeBytes())
            throw new IngestionException(ErrorKind.FILE_TOO_LARGE,
                    "PDF exceeds maxFileSizeBytes: " + size + " > " + config.maxFileSizeBytes());

        try (InputStream in = Files.newInputStream(pdfPath)) {
            byte[] header = in.readNBytes(PDF_MAGIC.length);
            if (header.length < PDF_MAGIC.length || !startsWith(header, PDF_MAGIC))
                throw new IngestionException(ErrorKind.INVALID_FORMAT,
                        "File does not start with PDF magic bytes: " + pdfPath);
        } catch (IngestionException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("could not read file header: " + pdfPath, ex);
        }
    }

    private static ExtractedText extractPages(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder text = new StringBuilder();
        List<PageSpan> spans = new ArrayList<>();

        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String pageText = normalize(stripper.getText(document));
            if (pageText.isBlank())
                continue;

            if (!text.isEmpty())
                text.append('\n');
            int startOffset = text.length();
            text.append(pageText);
            spans.add(new PageSpan(page, startOffset, text.length()));
        }

        return new ExtractedText(text.toString(), List.copyOf(spans));
    }

    private static String normalize(String text) {
        return text.replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .trim();
    }

    private static boolean startsWith(byte[] actual, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (actual[i] != expected[i])
                return false;
        }
        return true;
    }

    record ExtractedText(String text, List<PageSpan> pages) {
        int pageForOffset(int offset) {
            for (PageSpan page : pages) {
                if (offset >= page.startOffset() && offset < page.endOffset())
                    return page.pageNumber();
            }
            return pages.isEmpty() ? 1 : pages.getLast().pageNumber();
        }
    }

    private record PageSpan(int pageNumber, int startOffset, int endOffset) {}
}