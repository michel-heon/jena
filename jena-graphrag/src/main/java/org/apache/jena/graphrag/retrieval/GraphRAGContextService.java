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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graphrag.retrieval.GraphRAGContext.Result;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.text.DatasetGraphText;
import org.apache.jena.query.text.TextQuery;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * Retrieves context directly from a normalized GraphRAG RDF graph.
 * <p>
 * The service is the shared Java implementation behind the Fuseki context
 * endpoint. It reads RDF resources already present in the dataset, performs no
 * LLM call or external network request, and leaves transaction ownership to the
 * caller.
 */
public final class GraphRAGContextService {

    public static final String BASIC_MODE = "basic";
    public static final String LOCAL_MODE = "local";
    public static final String GLOBAL_MODE = "global";

    private static final int TEXT_EXPANSION_FACTOR = 5;

    private static final String BASIC_QUERY = """
                        PREFIX text: <http://jena.apache.org/text#>
                        PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?chunk ?score ?literal ?document
                        WHERE {
                            (?chunk ?score ?literal) text:query (mg:text ?query %d) .
                            ?chunk a mg:Chunk .
                            OPTIONAL { ?chunk mg:partOf ?document }
                        }
                        ORDER BY DESC(?score) STR(?chunk)
                        """;

    private static final String BASIC_FALLBACK_QUERY = """
                        PREFIX mg: <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?chunk ?literal ?document
                        WHERE {
                            ?chunk a mg:Chunk ; mg:text ?literal .
                            FILTER(CONTAINS(LCASE(STR(?literal)), LCASE(?query)))
                            OPTIONAL { ?chunk mg:partOf ?document }
                        }
                        ORDER BY STR(?chunk)
                        """;

    private static final String LOCAL_TEXT_QUERY = """
                        PREFIX text: <http://jena.apache.org/text#>
                        PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?chunk ?chunkScore ?literal ?document ?entity ?entityName ?rel ?neighbor ?neighborName
                                     ?description ?weight ?rank
                        WHERE {
                            (?chunk ?chunkScore ?literal) text:query (mg:text ?query %d) .
                            ?chunk a mg:Chunk .
                            OPTIONAL { ?chunk mg:partOf ?document }
                            {
                                ?chunk mg:hasEntity ?entity .
                            } UNION {
                                ?entity mg:hasEntity ?chunk .
                            }
                            ?entity a mg:Entity ; mg:name ?entityName .
                            ?rel a mg:Relationship .
                            {
                                ?rel mg:source ?entity ; mg:target ?neighbor .
                            } UNION {
                                ?rel mg:source ?neighbor ; mg:target ?entity .
                            }
                            ?neighbor mg:name ?neighborName .
                            OPTIONAL { ?rel mg:description ?description }
                            OPTIONAL { ?rel mg:weight ?weight }
                            OPTIONAL { ?rel mg:rank ?rank }
                        }
                        ORDER BY DESC(?chunkScore) DESC(?weight) STR(?rel)
                        """;

    private static final String LOCAL_QUERY = """
            PREFIX mg: <http://ormynet.com/ns/msft-graphrag#>
            SELECT ?entity ?entityName ?rel ?neighbor ?neighborName
                   ?description ?weight ?rank
            WHERE {
              ?entity a mg:Entity ; mg:name ?entityName .
              FILTER(CONTAINS(LCASE(STR(?entityName)), LCASE(?query)))
              ?rel a mg:Relationship ; mg:source ?entity ; mg:target ?neighbor .
              ?neighbor mg:name ?neighborName .
              OPTIONAL { ?rel mg:description ?description }
              OPTIONAL { ?rel mg:weight ?weight }
              OPTIONAL { ?rel mg:rank ?rank }
            }
            ORDER BY DESC(?weight) ?neighborName
            """;

    private static final String GLOBAL_QUERY = """
                        PREFIX text: <http://jena.apache.org/text#>
                        PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?community (MAX(?rawScore) AS ?score) (SAMPLE(?literal) AS ?literal) (SAMPLE(?title) AS ?title)
                        WHERE {
                            {
                                (?community ?rawScore ?literal) text:query (mg:summary ?query %d) .
                            } UNION {
                                (?community ?rawScore ?literal) text:query (mg:fullContent ?query %d) .
                            }
                            ?community a mg:Community .
                            OPTIONAL { ?community mg:title ?title }
                        }
                        GROUP BY ?community
                        ORDER BY DESC(?score) STR(?community)
                        """;

    /**
     * Retrieves relationship-backed local context for entities whose
     * {@code mg:name} or linked chunks match the query text.
     * <p>
     * The caller must open the appropriate dataset transaction. The service uses
     * only RDF and local indexes already attached to the dataset.
     *
     * @param datasetGraph normalized GraphRAG dataset; caller owns its transaction
     * @param query non-blank search text
     * @param topK maximum number of results, from 1 to 100 inclusive
     * @return cited local context, possibly with an empty result list
     * @throws IllegalArgumentException if {@code query} is blank or {@code topK} is outside bounds
     */
    public GraphRAGContext retrieve(DatasetGraph datasetGraph, String query, int topK) {
        return retrieve(datasetGraph, LOCAL_MODE, query, topK);
    }

    /**
     * Retrieves context for the requested GraphRAG mode.
     *
     * @param datasetGraph normalized GraphRAG dataset; caller owns its transaction
        * @param mode retrieval mode, either {@code basic}, {@code local} or {@code global}
     * @param query non-blank search text
     * @param topK maximum number of results, from 1 to 100 inclusive
     * @return cited context, possibly with an empty result list
     * @throws IllegalArgumentException if any request parameter is outside bounds
     */
    public GraphRAGContext retrieve(DatasetGraph datasetGraph, String mode, String query, int topK) {
        if ( query == null || query.isBlank() )
            throw new IllegalArgumentException("parametre 'q' requis");
        if ( topK < 1 || topK > 100 )
            throw new IllegalArgumentException("topK doit etre compris entre 1 et 100");
        if ( !supportsMode(mode) )
            throw new IllegalArgumentException("mode invalide: " + mode);

        if ( BASIC_MODE.equals(mode) )
            return retrieveBasic(datasetGraph, query, topK);
        if ( GLOBAL_MODE.equals(mode) )
            return retrieveGlobal(datasetGraph, query, topK);
        return retrieveLocal(datasetGraph, query, topK);
    }

    public static boolean supportsMode(String mode) {
        return BASIC_MODE.equals(mode) || LOCAL_MODE.equals(mode) || GLOBAL_MODE.equals(mode);
    }

    private static GraphRAGContext retrieveBasic(DatasetGraph datasetGraph, String query, int topK) {
        List<Result> results = hasTextIndex(datasetGraph)
                ? retrieveBasicWithTextIndex(datasetGraph, query, topK)
                : retrieveBasicFallback(datasetGraph, query, topK);
        return new GraphRAGContext(query, BASIC_MODE, List.copyOf(results));
    }

    private static List<Result> retrieveBasicWithTextIndex(DatasetGraph datasetGraph, String query, int topK) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(BASIC_QUERY.formatted(topK));
        contextQuery.setLimit(topK);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toChunkResult(resultSet.next(), true));
        }
        return results;
    }

    private static List<Result> retrieveBasicFallback(DatasetGraph datasetGraph, String query, int topK) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(BASIC_FALLBACK_QUERY);
        contextQuery.setLimit(topK);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toChunkResult(resultSet.next(), false));
        }
        return results;
    }

    private static boolean hasTextIndex(DatasetGraph datasetGraph) {
        return datasetGraph.getContext().get(TextQuery.textIndex) != null
                || datasetGraph instanceof DatasetGraphText;
    }

    private static GraphRAGContext retrieveLocal(DatasetGraph datasetGraph, String query, int topK) {
        List<Result> results = new ArrayList<>();
        if ( hasTextIndex(datasetGraph) )
            appendDistinct(results, retrieveLocalWithTextIndex(datasetGraph, query, topK), topK);
        if ( results.size() < topK )
            appendDistinct(results, retrieveLocalFallback(datasetGraph, query, topK), topK);
        return new GraphRAGContext(query, LOCAL_MODE, List.copyOf(results));
    }

    private static List<Result> retrieveLocalWithTextIndex(DatasetGraph datasetGraph, String query, int topK) {
        Model bindings = ModelFactory.createDefaultModel();
        int textTopK = Math.min(100, topK * TEXT_EXPANSION_FACTOR);
        Query contextQuery = QueryFactory.create(LOCAL_TEXT_QUERY.formatted(textTopK));
        contextQuery.setLimit(textTopK);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toLocalTextResult(resultSet.next()));
        }
        return results;
    }

    private static List<Result> retrieveLocalFallback(DatasetGraph datasetGraph, String query, int topK) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(LOCAL_QUERY);
        contextQuery.setLimit(topK);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toLocalResult(resultSet.next()));
        }
        return results;
    }

    private static void appendDistinct(List<Result> results, List<Result> candidates, int topK) {
        Set<String> knownUris = new HashSet<>();
        results.forEach(result -> knownUris.add(result.uri()));
        for ( Result candidate : candidates ) {
            if ( results.size() >= topK )
                return;
            if ( knownUris.add(candidate.uri()) )
                results.add(candidate);
        }
    }

    private static GraphRAGContext retrieveGlobal(DatasetGraph datasetGraph, String query, int topK) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(GLOBAL_QUERY.formatted(topK, topK));
        contextQuery.setLimit(topK);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toGlobalResult(resultSet.next()));
        }
        return new GraphRAGContext(query, GLOBAL_MODE, List.copyOf(results));
    }

    private static Result toLocalResult(QuerySolution solution) {
        String sourceText = solution.contains("description")
                ? solution.getLiteral("description").getString()
                : "";
        Double weight = solution.contains("weight")
                ? solution.getLiteral("weight").getDouble()
                : null;
        Integer rank = solution.contains("rank")
                ? solution.getLiteral("rank").getInt()
                : null;
        double score = weight != null ? weight : rank != null ? rank.doubleValue() : 1.0;
        return Result.relationship(
                solution.getResource("rel").getURI(), score, sourceText,
                solution.getResource("entity").getURI(),
                solution.getLiteral("entityName").getString(),
                solution.getResource("neighbor").getURI(),
                solution.getLiteral("neighborName").getString(),
                weight, rank);
    }

    private static Result toLocalTextResult(QuerySolution solution) {
        String sourceText = solution.contains("description")
                ? solution.getLiteral("description").getString()
                : solution.getLiteral("literal").getString();
        Double weight = solution.contains("weight")
                ? solution.getLiteral("weight").getDouble()
                : null;
        Integer rank = solution.contains("rank")
                ? solution.getLiteral("rank").getInt()
                : null;
        String documentUri = solution.contains("document")
                ? solution.getResource("document").getURI()
                : null;
        return Result.relationship(
                solution.getResource("rel").getURI(),
                solution.getLiteral("chunkScore").getDouble(),
                sourceText,
                solution.getResource("entity").getURI(),
                solution.getLiteral("entityName").getString(),
                solution.getResource("neighbor").getURI(),
                solution.getLiteral("neighborName").getString(),
                weight,
                rank,
                solution.getResource("chunk").getURI(),
                solution.getLiteral("literal").getString(),
                documentUri);
    }

    private static Result toChunkResult(QuerySolution solution, boolean scored) {
        String documentUri = solution.contains("document")
                ? solution.getResource("document").getURI()
                : null;
        double score = scored && solution.contains("score")
                ? solution.getLiteral("score").getDouble()
                : 1.0;
        return Result.chunk(
                solution.getResource("chunk").getURI(),
                score,
                solution.getLiteral("literal").getString(),
                documentUri);
    }

    private static Result toGlobalResult(QuerySolution solution) {
        String title = solution.contains("title")
                ? solution.getLiteral("title").getString()
                : "";
        String sourceText = solution.contains("literal")
                ? solution.getLiteral("literal").getString()
                : "";
        return Result.community(
                solution.getResource("community").getURI(),
                solution.getLiteral("score").getDouble(),
                sourceText,
                title);
    }
}