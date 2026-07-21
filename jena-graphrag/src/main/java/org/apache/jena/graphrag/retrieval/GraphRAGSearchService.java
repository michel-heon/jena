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

package org.apache.jena.graphrag.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.graphrag.index.EmbeddingProvider;
import org.apache.jena.graphrag.index.VectorIndex;
import org.apache.jena.graphrag.retrieval.HybridRanker.ScoredResult;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.text.DatasetGraphText;
import org.apache.jena.query.text.TextQuery;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;

/** Shared implementation behind hybrid text/vector GraphRAG search. */
public final class GraphRAGSearchService {

    private static final String TEXT_QUERY = """
            PREFIX text: <http://jena.apache.org/text#>
            PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

            SELECT ?chunk ?score WHERE {
              (?chunk ?score) text:query (mg:text ?needle %d) .
              ?chunk a mg:Chunk .
            }
            ORDER BY DESC(?score) STR(?chunk)
            """;

    private final VectorIndex vectorIndex;
    private final EmbeddingProvider embeddingProvider;
    private final int dimension;

    public GraphRAGSearchService(VectorIndex vectorIndex, EmbeddingProvider embeddingProvider, int dimension) {
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "vectorIndex");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
        if ( dimension < 1 )
            throw new IllegalArgumentException("dimension must be greater than zero");
        this.dimension = dimension;
    }

    public GraphRAGSearch search(DatasetGraph datasetGraph, String query, int topK, double hybridAlpha) {
        Objects.requireNonNull(datasetGraph, "datasetGraph");
        if ( query == null || query.isBlank() )
            throw new IllegalArgumentException("parametre 'q' requis");
        if ( topK < 1 )
            throw new IllegalArgumentException("topK must be greater than zero");

        List<ScoredResult> textResults = textResults(datasetGraph, query, topK);
        List<ScoredResult> vectorResults = vectorResults(query, topK);
        List<GraphRAGSearch.Result> results = new HybridRanker(hybridAlpha)
                .rank(textResults, vectorResults, topK)
                .stream()
                .map(result -> new GraphRAGSearch.Result(
                        result.uri(), result.textScore(), result.vectorScore(), result.score()))
                .toList();
        return new GraphRAGSearch(query, List.copyOf(results));
    }

    private static List<ScoredResult> textResults(DatasetGraph datasetGraph, String query, int topK) {
        if ( !hasTextIndex(datasetGraph) )
            return List.of();
        Query textQuery = QueryFactory.create(TEXT_QUERY.formatted(topK));
        var needle = ModelFactory.createDefaultModel().createLiteral(query);
        try (QueryExecution queryExecution = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(textQuery)
                .substitution("needle", needle)
                .build()) {
            ResultSet resultSet = queryExecution.execSelect();
            List<ScoredResult> results = new ArrayList<>();
            while ( resultSet.hasNext() ) {
            var solution = resultSet.nextSolution();
            var scoreLiteral = solution.getLiteral("score");
            double score = scoreLiteral != null ? scoreLiteral.getDouble() : 1.0;
            results.add(new ScoredResult(
                solution.getResource("chunk").getURI(),
                score));
            }
            return List.copyOf(results);
        }
    }

    private static boolean hasTextIndex(DatasetGraph datasetGraph) {
        return datasetGraph.getContext().get(TextQuery.textIndex) != null
                || datasetGraph instanceof DatasetGraphText;
    }

    private List<ScoredResult> vectorResults(String query, int topK) {
        float[] queryVector = Objects.requireNonNull(embeddingProvider.embed(query, dimension), "query vector");
        if ( queryVector.length != dimension )
            throw new IllegalArgumentException("query vector dimension must be " + dimension + ": " + queryVector.length);
        return vectorIndex.search(queryVector, topK).stream()
                .map(result -> new ScoredResult(result.uri(), result.score()))
                .toList();
    }
}