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

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic sliding-window chunker over normalized text.
 */
final class TextChunker {

    record TextChunk(int index, String text, int startPage, int endPage) {}

    private final int chunkSize;
    private final int chunkOverlap;

    TextChunker(DocumentIngestionConfig config) {
        this.chunkSize = config.chunkSize();
        this.chunkOverlap = config.chunkOverlap();
    }

    List<TextChunk> chunk(PdfTextExtractor.ExtractedText extractedText) {
        String text = extractedText.text();
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int end = chooseEnd(text, start);
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                int startPage = extractedText.pageForOffset(start);
                int endPage = extractedText.pageForOffset(Math.max(start, end - 1));
                chunks.add(new TextChunk(index++, chunkText, startPage, endPage));
            }
            if (end == text.length())
                break;
            start = Math.max(0, end - chunkOverlap);
        }
        return chunks;
    }

    private int chooseEnd(String text, int start) {
        int hardEnd = Math.min(text.length(), start + chunkSize);
        if (hardEnd == text.length())
            return hardEnd;

        for (int i = hardEnd; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i - 1)))
                return i;
        }
        return hardEnd;
    }
}