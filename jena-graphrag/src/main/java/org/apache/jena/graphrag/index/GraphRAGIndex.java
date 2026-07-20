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

package org.apache.jena.graphrag.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.jena.graphrag.provider.ChatCompletionProvider;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndex;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.FSDirectory;

/** Runtime handle for a configured GraphRAG text/vector index pair. */
public final class GraphRAGIndex implements AutoCloseable {
    static final int DEFAULT_VECTOR_DIMENSION = 384;

    private final boolean enabled;
    private final Path textIndexDirectory;
    private final Path vectorIndexDirectory;
    private final int vectorDimension;
    private final TextIndex textIndex;
    private final LuceneVectorIndex vectorIndex;
    private final EmbeddingProvider embeddingProvider;
    private final ChatCompletionProvider chatCompletionProvider;

    private GraphRAGIndex(boolean enabled, Path textIndexDirectory, Path vectorIndexDirectory, int vectorDimension,
                         TextIndex textIndex, LuceneVectorIndex vectorIndex, EmbeddingProvider embeddingProvider,
                         ChatCompletionProvider chatCompletionProvider) {
        this.enabled = enabled;
        this.textIndexDirectory = textIndexDirectory;
        this.vectorIndexDirectory = vectorIndexDirectory;
        this.vectorDimension = vectorDimension;
        this.textIndex = textIndex;
        this.vectorIndex = vectorIndex;
        this.embeddingProvider = embeddingProvider;
        this.chatCompletionProvider = chatCompletionProvider;
    }

    static GraphRAGIndex disabled() {
        return new GraphRAGIndex(false, null, null, 0, null, null, null, null);
    }

    static GraphRAGIndex open(Path textIndexDirectory, Path vectorIndexDirectory, int vectorDimension,
                              EmbeddingProvider embeddingProvider, ChatCompletionProvider chatCompletionProvider) {
        Objects.requireNonNull(textIndexDirectory, "textIndexDirectory");
        Objects.requireNonNull(vectorIndexDirectory, "vectorIndexDirectory");
        Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        Objects.requireNonNull(chatCompletionProvider, "chatCompletionProvider");
        try {
            TextIndexConfig textConfig = new TextIndexConfig(GraphRAGTextDatasetFactory.retrievalEntityDefinition());
            textConfig.setValueStored(true);
            TextIndex textIndex = TextDatasetFactory.createLuceneIndex(FSDirectory.open(textIndexDirectory), textConfig);
            LuceneVectorIndex vectorIndex = new LuceneVectorIndex(FSDirectory.open(vectorIndexDirectory), vectorDimension,
                    VectorSimilarityFunction.EUCLIDEAN);
                return new GraphRAGIndex(true, textIndexDirectory, vectorIndexDirectory, vectorDimension, textIndex, vectorIndex,
                    embeddingProvider, chatCompletionProvider);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open GraphRAG indexes", e);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public Path textIndexDirectory() {
        ensureEnabled();
        return textIndexDirectory;
    }

    public Path vectorIndexDirectory() {
        ensureEnabled();
        return vectorIndexDirectory;
    }

    public int vectorDimension() {
        ensureEnabled();
        return vectorDimension;
    }

    public TextIndex textIndex() {
        ensureEnabled();
        return textIndex;
    }

    public LuceneVectorIndex vectorIndex() {
        ensureEnabled();
        return vectorIndex;
    }

    public EmbeddingProvider embeddingProvider() {
        ensureEnabled();
        return embeddingProvider;
    }

    public ChatCompletionProvider chatCompletionProvider() {
        ensureEnabled();
        return chatCompletionProvider;
    }

    @Override
    public void close() {
        if ( !enabled )
            return;
        textIndex.close();
        vectorIndex.close();
    }

    private void ensureEnabled() {
        if ( !enabled )
            throw new IllegalStateException("GraphRAG index is disabled");
    }
}