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

import org.apache.jena.graphrag.retrieval.GraphRAGContext.Result;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.DatasetGraph;

/** Retrieves local entity context directly from the normalized RDF graph. */
public final class GraphRAGContextService {

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

    /**
     * Retrieves local context. The caller owns the dataset transaction.
     *
     * @param datasetGraph normalized GraphRAG dataset
     * @param query entity-name search text
     * @param topK maximum number of results
     */
    public GraphRAGContext retrieve(DatasetGraph datasetGraph, String query, int topK) {
        if ( query == null || query.isBlank() )
            throw new IllegalArgumentException("parametre 'q' requis");
        if ( topK < 1 || topK > 100 )
            throw new IllegalArgumentException("topK doit etre compris entre 1 et 100");

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
                results.add(toResult(resultSet.next()));
        }
        return new GraphRAGContext(query, "local", List.copyOf(results));
    }

    private static Result toResult(QuerySolution solution) {
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
        return new Result(
                solution.getResource("rel").getURI(), score, sourceText,
                "relationship",
                solution.getResource("entity").getURI(),
                solution.getLiteral("entityName").getString(),
                solution.getResource("neighbor").getURI(),
                solution.getLiteral("neighborName").getString(),
                weight, rank);
    }
}