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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

/** Lucene-backed GraphRAG vector index using {@link KnnFloatVectorField}. */
public final class LuceneVectorIndex implements VectorIndex {

    public static final int MAX_DIMENSION = 1024;

    private static final String URI_FIELD = "uri";
    private static final String VECTOR_FIELD = "vector";

    private final Directory directory;
    private final int dimension;
    private final VectorSimilarityFunction similarityFunction;
    private final IndexWriter writer;
    private boolean closed;

    public LuceneVectorIndex(Directory directory, int dimension, VectorSimilarityFunction similarityFunction) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.dimension = validateDimension(dimension);
        this.similarityFunction = Objects.requireNonNull(similarityFunction, "similarityFunction");
        try {
            this.writer = new IndexWriter(directory, new IndexWriterConfig());
            this.writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open Lucene vector index", e);
        }
    }

    @Override
    public void index(String uri, float[] vector) {
        ensureOpen();
        validateUri(uri);
        validateVector("vector", vector);

        Document document = new Document();
        document.add(new StringField(URI_FIELD, uri, Field.Store.YES));
        document.add(new KnnFloatVectorField(VECTOR_FIELD, vector, similarityFunction));
        try {
            writer.updateDocument(new Term(URI_FIELD, uri), document);
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to index vector for " + uri, e);
        }
    }

    @Override
    public boolean contains(String uri) {
        ensureOpen();
        validateUri(uri);
        try {
            writer.commit();
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                return searcher.count(new TermQuery(new Term(URI_FIELD, uri))) > 0;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to inspect Lucene vector index for " + uri, e);
        }
    }

    @Override
    public List<VectorResult> search(float[] queryVector, int k) {
        ensureOpen();
        validateVector("queryVector", queryVector);
        if ( k < 1 )
            throw new IllegalArgumentException("k must be greater than zero");

        try {
            writer.commit();
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs topDocs = searcher.search(new KnnFloatVectorQuery(VECTOR_FIELD, queryVector, k), k);
                List<VectorResult> results = new ArrayList<>(topDocs.scoreDocs.length);
                for ( ScoreDoc scoreDoc : topDocs.scoreDocs ) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    results.add(new VectorResult(document.get(URI_FIELD), scoreDoc.score));
                }
                return List.copyOf(results);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to search Lucene vector index", e);
        }
    }

    @Override
    public void close() {
        if ( closed )
            return;
        try {
            writer.close();
            directory.close();
            closed = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to close Lucene vector index", e);
        }
    }

    private static int validateDimension(int dimension) {
        if ( dimension < 1 || dimension > MAX_DIMENSION )
            throw new IllegalArgumentException("dimension must be between 1 and " + MAX_DIMENSION + ": " + dimension);
        return dimension;
    }

    private static void validateUri(String uri) {
        if ( uri == null || uri.isBlank() )
            throw new IllegalArgumentException("uri must not be blank");
    }

    private void validateVector(String label, float[] vector) {
        Objects.requireNonNull(vector, label);
        if ( vector.length != dimension )
            throw new IllegalArgumentException(label + " dimension must be " + dimension + ": " + vector.length);
    }

    private void ensureOpen() {
        if ( closed )
            throw new IllegalStateException("LuceneVectorIndex is closed");
    }
}