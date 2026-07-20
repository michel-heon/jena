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

package org.apache.jena.graphrag.fuseki;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.fuseki.servlets.ActionREST;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.graphrag.index.VectorIndex;
import org.apache.jena.graphrag.index.VectorResult;
import org.apache.jena.graphrag.retrieval.GraphRAGSearch;
import org.apache.jena.graphrag.retrieval.GraphRAGSearch.Result;
import org.apache.jena.graphrag.retrieval.GraphRAGSearchService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.web.HttpSC;

/** Experimental {@code /{dataset}/graphrag/search} operation registered by {@link GraphRAGModule}. */
public final class GraphRAGSearchAction extends ActionREST {

    private final DatasetGraph datasetGraph;
    private final GraphRAGConfiguration configuration;
    private final GraphRAGSearchService searchService;

    GraphRAGSearchAction(DatasetGraph datasetGraph, GraphRAGConfiguration configuration) {
        this(datasetGraph, configuration, defaultSearchService());
    }

    GraphRAGSearchAction(DatasetGraph datasetGraph, GraphRAGConfiguration configuration, GraphRAGSearchService searchService) {
        this.datasetGraph = Objects.requireNonNull(datasetGraph);
        this.configuration = Objects.requireNonNull(configuration);
        this.searchService = Objects.requireNonNull(searchService);
    }

    @Override
    public void validate(HttpAction action) {}

    @Override
    protected void doGet(HttpAction action) {
        executeSearch(action);
    }

    @Override
    protected void doPost(HttpAction action) {
        executeSearch(action);
    }

    private void executeSearch(HttpAction action) {
        String query = action.getRequestParameter("q");
        if ( query == null || query.isBlank() ) {
            writeError(action, "parametre 'q' requis");
            return;
        }
        Integer topK = parseTopK(action);
        if ( topK == null )
            return;

        datasetGraph.begin(ReadWrite.READ);
        try {
            GraphRAGSearch search = searchService.search(datasetGraph, query, topK, configuration.hybridAlpha());
            writeJson(action, search);
        } finally {
            datasetGraph.end();
        }
    }

    private Integer parseTopK(HttpAction action) {
        String value = parameter(action, "topK", Integer.toString(configuration.defaultTopK()));
        try {
            int topK = Integer.parseInt(value);
            if ( topK < 1 || topK > configuration.maxTopK() ) {
                writeError(action, "topK doit etre compris entre 1 et " + configuration.maxTopK());
                return null;
            }
            return topK;
        } catch (NumberFormatException ex) {
            writeError(action, "topK invalide: " + value);
            return null;
        }
    }

    private static String parameter(HttpAction action, String name, String defaultValue) {
        String value = action.getRequestParameter(name);
        return value == null ? defaultValue : value;
    }

    private static void writeJson(HttpAction action, GraphRAGSearch search) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject()
               .pair("query", search.query())
               .key("results").startArray();
        search.results().forEach(result -> addResult(builder, result));
        builder.finishArray().finishObject();
        writeJson(action, builder.build(), HttpSC.OK_200);
    }

    private static void writeError(HttpAction action, String message) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject().pair("error", message).finishObject();
        writeJson(action, builder.build(), HttpSC.BAD_REQUEST_400);
    }

    private static void writeJson(HttpAction action, JsonValue json, int status) {
        action.setResponseStatus(status);
        action.setResponseContentType(WebContent.contentTypeJSON);
        action.setResponseCharacterEncoding(WebContent.charsetUTF8);
        try {
            OutputStream out = action.getResponseOutputStream();
            JSON.write(out, json);
            out.write('\n');
            out.flush();
        } catch (IOException ex) {
            ServletOps.errorOccurred(ex);
        }
    }

    private static void addResult(JsonBuilder builder, Result result) {
        builder.startObject()
               .pair("uri", result.uri())
               .pair("scoreText", result.scoreText())
               .pair("scoreVector", result.scoreVector())
               .pair("scoreHybrid", result.scoreHybrid())
               .finishObject();
    }

    static GraphRAGSearchService defaultSearchService() {
        return new GraphRAGSearchService(new EmptyVectorIndex(), (text, dimension) -> new float[dimension], 1);
    }

    private static final class EmptyVectorIndex implements VectorIndex {
        @Override
        public void index(String uri, float[] vector) {}

        @Override
        public boolean contains(String uri) {
            return false;
        }

        @Override
        public List<VectorResult> search(float[] queryVector, int k) {
            return List.of();
        }

        @Override
        public void close() {}
    }

    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}