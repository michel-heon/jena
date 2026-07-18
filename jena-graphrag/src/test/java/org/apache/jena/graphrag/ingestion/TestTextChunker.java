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

import java.util.List;

import org.junit.jupiter.api.Test;

public class TestTextChunker {

    @Test
    void chunk_shortText_returnsSingleTrimmedChunk() {
        TextChunker chunker = new TextChunker(config(50, 10));

        List<TextChunker.TextChunk> chunks = chunker.chunk(extracted("  short PDF text  "));

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.getFirst().index());
        assertEquals("short PDF text", chunks.getFirst().text());
        assertEquals(1, chunks.getFirst().startPage());
        assertEquals(1, chunks.getFirst().endPage());
    }

    @Test
    void chunk_longTextWithoutWhitespace_fallsBackToHardWindow() {
        TextChunker chunker = new TextChunker(config(50, 10));

        List<TextChunker.TextChunk> chunks = chunker.chunk(extracted("x".repeat(120)));

        assertEquals(3, chunks.size());
        assertEquals(50, chunks.get(0).text().length());
        assertEquals(50, chunks.get(1).text().length());
        assertEquals(40, chunks.get(2).text().length());
        assertSequentialIndexes(chunks);
    }

    @Test
    void chunk_longTextWithWhitespace_prefersWordBoundaryBeforeHardWindow() {
        TextChunker chunker = new TextChunker(config(50, 10));

        List<TextChunker.TextChunk> chunks = chunker.chunk(extracted("word ".repeat(30)));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= 50));
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.text().isBlank()));
        assertTrue(chunks.getFirst().text().endsWith("word"));
        assertSequentialIndexes(chunks);
    }

    @Test
    void chunk_sameInput_returnsDeterministicChunks() {
        TextChunker chunker = new TextChunker(config(50, 10));
        PdfTextExtractor.ExtractedText extractedText = extracted("alpha beta gamma delta epsilon ".repeat(8));

        List<TextChunker.TextChunk> firstRun = chunker.chunk(extractedText);
        List<TextChunker.TextChunk> secondRun = chunker.chunk(extractedText);

        assertEquals(firstRun, secondRun);
    }

    private DocumentIngestionConfig config(int chunkSize, int chunkOverlap) {
        return new DocumentIngestionConfig(
                DocumentIngestionConfig.DEFAULT_BASE_URI,
                chunkSize,
                chunkOverlap,
                DocumentIngestionConfig.DEFAULT_MAX_FILE_SIZE_BYTES);
    }

    private PdfTextExtractor.ExtractedText extracted(String text) {
        return new PdfTextExtractor.ExtractedText(text, List.of());
    }

    private void assertSequentialIndexes(List<TextChunker.TextChunk> chunks) {
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++)
            assertEquals(chunkIndex, chunks.get(chunkIndex).index());
    }
}