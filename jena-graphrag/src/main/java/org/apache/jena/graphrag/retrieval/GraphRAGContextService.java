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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;

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

    private final GraphRAGSearchService searchService;

    public GraphRAGContextService() {
        this(null);
    }

    /** Creates a context service that uses the supplied vector search for basic mode. */
    public GraphRAGContextService(GraphRAGSearchService searchService) {
        this.searchService = searchService;
    }

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

    private static final String GLOBAL_FALLBACK_QUERY = """
                        PREFIX mg: <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?community (SAMPLE(?literal) AS ?literal) (SAMPLE(?title) AS ?title) (MIN(?level) AS ?level)
                        WHERE {
                            ?community a mg:Community .
                            OPTIONAL { ?community mg:title ?title }
                            OPTIONAL { ?community mg:level ?level }
                            {
                                ?community mg:summary ?literal .
                            } UNION {
                                ?community mg:fullContent ?literal .
                            }
                            FILTER(CONTAINS(LCASE(STR(?literal)), LCASE(?query)))
                        }
                        GROUP BY ?community
                        ORDER BY ASC(?level) STR(?community)
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
                FILTER(CONTAINS(LCASE(STR(?entityName)), LCASE(?query))
                    || CONTAINS(LCASE(?query), LCASE(STR(?entityName))))
              ?rel a mg:Relationship ; mg:source ?entity ; mg:target ?neighbor .
              ?neighbor mg:name ?neighborName .
              OPTIONAL { ?rel mg:description ?description }
              OPTIONAL { ?rel mg:weight ?weight }
              OPTIONAL { ?rel mg:rank ?rank }
            }
            ORDER BY DESC(?weight) ?neighborName
            """;

        private static final String LOCAL_COMMUNITY_QUERY = """
                        PREFIX mg: <http://ormynet.com/ns/msft-graphrag#>
                        SELECT ?community (SAMPLE(?literal) AS ?literal) (SAMPLE(?title) AS ?title) (MIN(?level) AS ?level)
                        WHERE {
                            ?entity mg:inCommunity ?community .
                            ?community a mg:Community .
                            OPTIONAL { ?community mg:title ?title }
                            OPTIONAL { ?community mg:level ?level }
                            {
                                ?community mg:summary ?literal .
                            } UNION {
                                ?community mg:fullContent ?literal .
                            }
                        }
                        GROUP BY ?community
                        ORDER BY ASC(?level) STR(?community)
                        """;

    private static final String LOCAL_CHUNK_QUERY = """
                        PREFIX mg: <http://ormynet.com/ns/msft-graphrag#>
                        SELECT ?chunk ?literal ?document
                        WHERE {
                            ?chunk a mg:Chunk ; mg:text ?literal .
                            OPTIONAL { ?chunk mg:partOf ?document }
                            {
                                ?chunk mg:hasEntity ?entity .
                            } UNION {
                                ?entity mg:hasEntity ?chunk .
                            }
                        }
                        ORDER BY STR(?chunk)
                        """;

    private static final String GLOBAL_QUERY = """
                        PREFIX text: <http://jena.apache.org/text#>
                        PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                        SELECT ?community (MAX(?rawScore) AS ?score) (SAMPLE(?literal) AS ?literal) (SAMPLE(?title) AS ?title) (MIN(?level) AS ?level)
                        WHERE {
                            {
                                (?community ?rawScore ?literal) text:query (mg:summary ?query %d) .
                            } UNION {
                                (?community ?rawScore ?literal) text:query (mg:fullContent ?query %d) .
                            }
                            ?community a mg:Community .
                            OPTIONAL { ?community mg:title ?title }
                            OPTIONAL { ?community mg:level ?level }
                        }
                        GROUP BY ?community
                        ORDER BY ASC(?level) DESC(?score) STR(?community)
                        """;

    /**
    * Retrieves local context for entities whose {@code mg:name} or linked
    * chunks match the query text. The result combines relevant relationships
    * with their entity, chunk and community-report provenance while respecting
    * the requested result limit.
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

    private GraphRAGContext retrieveBasic(DatasetGraph datasetGraph, String query, int topK) {
        if ( searchService != null )
            return new GraphRAGContext(query, BASIC_MODE, retrieveBasicWithVectorIndex(datasetGraph, query, topK));
        String searchTerm = searchTerm(query);
        List<Result> results = new ArrayList<>();
        if ( hasTextIndex(datasetGraph) )
            appendDistinct(results, retrieveBasicWithTextIndex(datasetGraph, searchTerm, topK), topK);
        if ( results.size() < topK )
            appendDistinct(results, retrieveBasicFallback(datasetGraph, searchTerm, topK), topK);
        return new GraphRAGContext(query, BASIC_MODE, List.copyOf(results));
    }

    private List<Result> retrieveBasicWithVectorIndex(DatasetGraph datasetGraph, String query, int topK) {
        Model model = DatasetFactory.wrap(datasetGraph).getDefaultModel();
        List<Result> results = new ArrayList<>();
        for ( GraphRAGSearch.Result vectorResult : searchService.searchVector(query, topK).results() ) {
            Resource chunk = model.getResource(vectorResult.uri());
            Statement text = chunk.getProperty(GRAG.text);
            if ( !model.contains(chunk, RDF.type, GRAG.Chunk) || text == null )
                continue;
            Statement document = chunk.getProperty(GRAG.partOf);
            results.add(Result.chunk(chunk.getURI(), vectorResult.scoreVector(), text.getString(),
                    document == null || !document.getObject().isResource() ? null : document.getResource().getURI()));
        }
        return List.copyOf(results);
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
        int relationshipLimit = topK <= 3 ? topK : topK - 3;
        List<Result> relationships = new ArrayList<>();
        if ( hasTextIndex(datasetGraph) ) {
            List<Result> textCandidates = retrieveLocalWithTextIndex(datasetGraph, query, relationshipLimit);
            List<Result> namedEntityCandidates = textCandidates.stream()
                    .filter(relationship -> entityNameMatchesQuery(relationship.entityName(), query))
                    .toList();
            appendDistinct(relationships, namedEntityCandidates.isEmpty() ? textCandidates : namedEntityCandidates, relationshipLimit);
        }
        if ( relationships.size() < relationshipLimit )
            appendDistinct(relationships, retrieveLocalFallback(datasetGraph, query, relationshipLimit), relationshipLimit);
        List<Result> namedEntityRelationships = relationships.stream()
                .filter(relationship -> entityNameMatchesQuery(relationship.entityName(), query))
                .toList();
        if ( !namedEntityRelationships.isEmpty() )
            relationships = namedEntityRelationships;

        List<Result> results = new ArrayList<>();
        appendDistinct(results, relationships, topK);
        for ( Result relationship : relationships ) {
            if ( results.size() >= topK )
                break;
            appendDistinct(results, List.of(Result.entity(relationship.entityUri(), relationship.score(),
                    relationship.entityName())), topK);
        }
        List<Result> communities = new ArrayList<>();
        Set<String> communityEntities = new HashSet<>();
        for ( Result relationship : relationships ) {
            if ( communityEntities.add(relationship.entityUri()) )
                appendDistinct(communities, retrieveLocalCommunities(datasetGraph, relationship.entityUri()), topK);
        }
        int chunkLimit = topK - Math.min(communities.size(), 1);
        for ( Result relationship : relationships ) {
            if ( results.size() >= chunkLimit )
                break;
            if ( relationship.chunkUri() != null )
                appendDistinct(results, List.of(Result.chunk(relationship.chunkUri(), relationship.score(),
                        relationship.chunkText(), relationship.documentUri())), chunkLimit);
        }
        Set<String> chunkEntities = new HashSet<>();
        for ( Result relationship : relationships ) {
            if ( results.size() >= chunkLimit )
                break;
            if ( chunkEntities.add(relationship.entityUri()) )
                appendDistinct(results, retrieveLocalChunks(datasetGraph, relationship.entityUri()), chunkLimit);
        }
        appendDistinct(results, communities, topK);
        return new GraphRAGContext(query, LOCAL_MODE, List.copyOf(results));
    }

    private static boolean entityNameMatchesQuery(String entityName, String query) {
        return query.toLowerCase(java.util.Locale.ROOT).contains(entityName.toLowerCase(java.util.Locale.ROOT));
    }

    private static List<Result> retrieveLocalCommunities(DatasetGraph datasetGraph, String entityUri) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(LOCAL_COMMUNITY_QUERY);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("entity", bindings.createResource(entityUri))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toGlobalResult(resultSet.next()));
        }
        return results;
    }

    private static List<Result> retrieveLocalChunks(DatasetGraph datasetGraph, String entityUri) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(LOCAL_CHUNK_QUERY);
        List<Result> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("entity", bindings.createResource(entityUri))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toChunkResult(resultSet.next(), false));
        }
        return results;
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
        String searchTerm = searchTerm(query);
        int candidateLimit = Math.min(100, topK * TEXT_EXPANSION_FACTOR);
        List<GlobalCandidate> candidates = new ArrayList<>();
        if ( hasTextIndex(datasetGraph) )
            appendGlobalCandidates(candidates, retrieveGlobalWithTextIndex(datasetGraph, searchTerm, candidateLimit), candidateLimit);
        if ( candidates.size() < candidateLimit )
            appendGlobalCandidates(candidates, retrieveGlobalFallback(datasetGraph, searchTerm, candidateLimit), candidateLimit);
        return new GraphRAGContext(query, GLOBAL_MODE, selectGlobalReports(candidates, topK));
    }

    private static String searchTerm(String query) {
        String[] terms = query.split("[^A-Za-z0-9]+");
        String selected = query;
        for ( String term : terms ) {
            if ( term.length() > selected.length() || selected.equals(query) && term.length() >= 3 )
                selected = term;
        }
        return selected;
    }

    private static List<GlobalCandidate> retrieveGlobalWithTextIndex(DatasetGraph datasetGraph, String query, int candidateLimit) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(GLOBAL_QUERY.formatted(candidateLimit, candidateLimit));
        contextQuery.setLimit(candidateLimit);
        List<GlobalCandidate> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toGlobalCandidate(resultSet.next()));
        }
        return results;
    }

    private static List<GlobalCandidate> retrieveGlobalFallback(DatasetGraph datasetGraph, String query, int candidateLimit) {
        Model bindings = ModelFactory.createDefaultModel();
        Query contextQuery = QueryFactory.create(GLOBAL_FALLBACK_QUERY);
        contextQuery.setLimit(candidateLimit);
        List<GlobalCandidate> results = new ArrayList<>();
        try (QueryExecution qexec = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph))
                .query(contextQuery)
                .substitution("query", bindings.createLiteral(query))
                .build()) {
            ResultSet resultSet = qexec.execSelect();
            while ( resultSet.hasNext() )
                results.add(toGlobalCandidate(resultSet.next()));
        }
        return results;
    }

    private static void appendGlobalCandidates(List<GlobalCandidate> candidates, List<GlobalCandidate> additions, int limit) {
        Set<String> knownUris = new HashSet<>();
        candidates.forEach(candidate -> knownUris.add(candidate.result().uri()));
        for ( GlobalCandidate candidate : additions ) {
            if ( candidates.size() >= limit )
                return;
            if ( knownUris.add(candidate.result().uri()) )
                candidates.add(candidate);
        }
    }

    private static List<Result> selectGlobalReports(List<GlobalCandidate> candidates, int topK) {
        List<Result> results = new ArrayList<>();
        Set<Integer> levels = new HashSet<>();
        for ( GlobalCandidate candidate : candidates ) {
            if ( results.size() >= topK )
                return List.copyOf(results);
            if ( levels.add(candidate.level()) )
                results.add(candidate.result());
        }
        for ( GlobalCandidate candidate : candidates ) {
            if ( results.size() >= topK )
                break;
            if ( !results.contains(candidate.result()) )
                results.add(candidate.result());
        }
        return List.copyOf(results);
    }

    private static GlobalCandidate toGlobalCandidate(QuerySolution solution) {
        int level = solution.contains("level") ? solution.getLiteral("level").getInt() : Integer.MAX_VALUE;
        return new GlobalCandidate(toGlobalResult(solution), level);
    }

    private record GlobalCandidate(Result result, int level) {}

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
            solution.contains("score") ? solution.getLiteral("score").getDouble() : 1.0,
                sourceText,
                title);
    }
}