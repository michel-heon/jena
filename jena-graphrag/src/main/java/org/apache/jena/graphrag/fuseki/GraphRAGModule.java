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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonException;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.servlets.ActionREST;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.web.HttpSC;

/**
 * Fuseki SPI module that registers optional GraphRAG HTTP operations.
 * <p>
 * Registration is opt-in: no processor is added unless the Fuseki configuration
 * model contains {@code grag:enableGraphRAG true}. The current processors expose
 * context, hybrid search and Phase 4 operator endpoints.
 */
public final class GraphRAGModule implements FusekiModule {

    /** Namespace for GraphRAG Fuseki configuration terms such as {@code enableGraphRAG}. */
    public static final String CONFIG_NS = "https://jena.apache.org/graphrag/vocab#";

    private final BiFunction<DatasetGraph, GraphRAGConfiguration, GraphRAGSearchAction> searchActionFactory;

    /** Constructor used by Java SPI; configuration remains opt-in. */
    public GraphRAGModule() {
        this(GraphRAGSearchAction::new);
    }

    GraphRAGModule(BiFunction<DatasetGraph, GraphRAGConfiguration, GraphRAGSearchAction> searchActionFactory) {
        this.searchActionFactory = Objects.requireNonNull(searchActionFactory);
    }

    @Override
    public String name() {
        return "GraphRAG";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        if ( !isEnabled(configModel) )
            return;
        GraphRAGConfiguration configuration = GraphRAGConfiguration.fromModel(configModel);
        datasetNames.forEach(name -> {
            DatasetGraph datasetGraph = builder.getDataset(name);
            GraphRAGTaskService taskService = new GraphRAGTaskService(configuration.maxActiveTasks(),
                    configuration.maxRetainedCompletedTasks());
            GraphRAGIndexingService indexingService = new GraphRAGIndexingService(datasetGraph, taskService, configuration);
            builder.addProcessor(name + "/graphrag/context", new GraphRAGContextAction(datasetGraph, configuration));
            builder.addProcessor(name + "/graphrag/search", searchActionFactory.apply(datasetGraph, configuration));
            builder.addProcessor(name + "/graphrag/index", new GraphRAGIndexAction(indexingService, configuration));
            builder.addProcessor(name + "/graphrag/status", new GraphRAGStatusAction(datasetGraph, taskService));
            builder.addProcessor(name + "/graphrag/config", new GraphRAGConfigAction(configuration));
        });
    }

    /**
     * Tests whether the configuration model explicitly enables GraphRAG.
     *
     * @param configModel Fuseki configuration model, or {@code null}
     * @return {@code true} only when {@code grag:enableGraphRAG true} is present
     */
    static boolean isEnabled(Model configModel) {
        if ( configModel == null )
            return false;
        Property enabled = configModel.createProperty(CONFIG_NS + "enableGraphRAG");
        return configModel.contains(null, enabled, configModel.createTypedLiteral(true));
    }
}

final class GraphRAGIndexAction extends ActionREST {
    private final GraphRAGIndexingService indexingService;
    private final GraphRAGConfiguration configuration;

    GraphRAGIndexAction(GraphRAGIndexingService indexingService, GraphRAGConfiguration configuration) {
        this.indexingService = Objects.requireNonNull(indexingService);
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Override
    public void validate(HttpAction action) {}

    @Override
    protected void doPost(HttpAction action) {
        try {
            GraphRAGTask task = indexingService.submit(readRequest(action));
            JsonBuilder builder = new JsonBuilder();
            builder.startObject()
                   .pair("status", "accepted")
                   .pair("taskId", task.taskId())
                   .finishObject();
            GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.ACCEPTED_202);
        } catch (GraphRAGBadRequestException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, ex.code(), ex.getMessage());
        } catch (GraphRAGTaskService.TaskLimitExceededException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "task_limit_exceeded", ex.getMessage());
        } catch (RuntimeException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.INTERNAL_SERVER_ERROR_500, "internal_error",
                    "erreur interne GraphRAG");
        }
    }

    private GraphRAGIndexRequest readRequest(HttpAction action) {
        long requestLength = action.getRequestContentLengthLong();
        if ( requestLength > configuration.maxIndexContentLength() )
            throw new GraphRAGBadRequestException("content_too_large",
                    "contenu trop volumineux: maximum " + configuration.maxIndexContentLength() + " caracteres");
        try {
            JsonObject body = JSON.parse(action.getRequestInputStream());
            return new GraphRAGIndexRequest(requiredString(body, "title"), requiredString(body, "content"),
                    requiredString(body, "sourceUri"));
        } catch (IOException | JsonException | ClassCastException ex) {
            throw new GraphRAGBadRequestException("invalid_json", "corps JSON invalide");
        }
    }

    private static String requiredString(JsonObject body, String field) {
        JsonValue value = body.get(field);
        if ( value == null || !value.isString() )
            throw new GraphRAGBadRequestException("invalid_request", "champ JSON requis: " + field);
        String string = value.getAsString().value();
        if ( string.isBlank() )
            throw new GraphRAGBadRequestException("invalid_request", "champ JSON vide: " + field);
        return string;
    }

    @Override protected void doGet(HttpAction action)     { ServletOps.errorMethodNotAllowed("GET"); }
    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}

final class GraphRAGStatusAction extends ActionREST {
    private final DatasetGraph datasetGraph;
    private final GraphRAGTaskService taskService;

    GraphRAGStatusAction(DatasetGraph datasetGraph, GraphRAGTaskService taskService) {
        this.datasetGraph = Objects.requireNonNull(datasetGraph);
        this.taskService = Objects.requireNonNull(taskService);
    }

    @Override
    public void validate(HttpAction action) {}

    @Override
    protected void doGet(HttpAction action) {
        String taskId = action.getRequestParameter("taskId");
        if ( taskId != null ) {
            writeTaskStatus(action, taskId);
            return;
        }
        writeSummary(action);
    }

    private void writeTaskStatus(HttpAction action, String taskId) {
        if ( taskId.isBlank() ) {
            GraphRAGHttpJson.writeError(action, HttpSC.BAD_REQUEST_400, "invalid_request", "taskId ne doit pas etre vide");
            return;
        }
        GraphRAGTask task = taskService.find(taskId).orElse(null);
        if ( task == null ) {
            GraphRAGHttpJson.writeError(action, HttpSC.NOT_FOUND_404, "task_not_found",
                    "tache GraphRAG inconnue: " + taskId);
            return;
        }
        GraphRAGHttpJson.writeJson(action, taskJson(task), HttpSC.OK_200);
    }

    private void writeSummary(HttpAction action) {
        GraphRAGTaskSummary summary = taskService.summary();
        datasetGraph.begin(ReadWrite.READ);
        try {
            JsonBuilder builder = new JsonBuilder();
            builder.startObject()
                   .pair("activeTasks", summary.activeTasks())
                   .pair("completedToday", summary.completedToday())
                   .pair("failedToday", summary.failedToday());
            if ( summary.lastSuccess() == null )
                builder.key("lastSuccess").valueNull();
            else
                builder.pair("lastSuccess", summary.lastSuccess().toString());
            builder.key("indexStats").startObject();
            indexStats().forEach(builder::pair);
            builder.finishObject().finishObject();
            GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.OK_200);
        } catch (RuntimeException ex) {
            GraphRAGHttpJson.writeError(action, HttpSC.INTERNAL_SERVER_ERROR_500, "internal_error",
                    "erreur interne GraphRAG");
        } finally {
            datasetGraph.end();
        }
    }

    private Map<String, Long> indexStats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("documents", countType(GRAG.Document.getURI()));
        counts.put("chunks", countType(GRAG.Chunk.getURI()));
        counts.put("entities", countType(GRAG.Entity.getURI()));
        counts.put("relationships", countType(GRAG.Relationship.getURI()));
        counts.put("communities", countType(GRAG.Community.getURI()));
        counts.put("findings", countType(GRAG.Finding.getURI()));
        return counts;
    }

    private long countType(String typeUri) {
        try (QueryExecution queryExecution = QueryExecution.dataset(DatasetFactory.wrap(datasetGraph)).query("""
                SELECT (COUNT(?resource) AS ?count) WHERE {
                  ?resource a <%s> .
                }
                """.formatted(typeUri)).build()) {
            ResultSet results = queryExecution.execSelect();
            if ( !results.hasNext() )
                return 0L;
            return results.nextSolution().getLiteral("count").getLong();
        }
    }

    static JsonValue taskJson(GraphRAGTask task) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject()
               .pair("taskId", task.taskId())
               .pair("status", task.status().jsonValue())
               .pair("createdAt", task.createdAt().toString());
        if ( task.startedAt() != null )
            builder.pair("startedAt", task.startedAt().toString());
        if ( task.completedAt() != null )
            builder.pair("completedAt", task.completedAt().toString());
        if ( task.error() != null )
            builder.key("error").startObject().pair("message", task.error()).finishObject();
        builder.finishObject();
        return builder.build();
    }

    @Override protected void doPost(HttpAction action)    { ServletOps.errorMethodNotAllowed("POST"); }
    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}

final class GraphRAGConfigAction extends ActionREST {
    private final GraphRAGConfiguration configuration;

    GraphRAGConfigAction(GraphRAGConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Override
    public void validate(HttpAction action) {}

    @Override
    protected void doGet(HttpAction action) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject()
               .pair("enabled", true)
               .key("modes").startArray()
               .value("basic")
               .value("local")
               .value("global")
               .finishArray()
               .pair("defaultMode", configuration.defaultMode())
               .pair("defaultTopK", configuration.defaultTopK())
               .pair("maxTopK", configuration.maxTopK())
               .pair("hybridAlpha", configuration.hybridAlpha())
               .key("limits").startObject()
               .pair("maxIndexContentLength", configuration.maxIndexContentLength())
               .pair("maxActiveTasks", configuration.maxActiveTasks())
               .pair("maxRetainedCompletedTasks", configuration.maxRetainedCompletedTasks())
               .finishObject()
               .key("maskedProperties").startObject();
        maskedGraphRAGProperties().forEach(builder::pair);
        builder.finishObject().finishObject();
        GraphRAGHttpJson.writeJson(action, builder.build(), HttpSC.OK_200);
    }

    private static Map<String, String> maskedGraphRAGProperties() {
        Map<String, String> properties = new TreeMap<>();
        for ( String name : System.getProperties().stringPropertyNames() ) {
            if ( name.startsWith("jena.graphrag.") && isSensitiveName(name) )
                properties.put(name, "***");
        }
        return properties;
    }

    private static boolean isSensitiveName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("apikey") || lower.contains("api_key") || lower.contains("secret")
                || lower.contains("token") || lower.contains("password") || lower.contains("credential");
    }

    @Override protected void doPost(HttpAction action)    { ServletOps.errorMethodNotAllowed("POST"); }
    @Override protected void doHead(HttpAction action)    { ServletOps.errorMethodNotAllowed("HEAD"); }
    @Override protected void doPut(HttpAction action)     { ServletOps.errorMethodNotAllowed("PUT"); }
    @Override protected void doDelete(HttpAction action)  { ServletOps.errorMethodNotAllowed("DELETE"); }
    @Override protected void doPatch(HttpAction action)   { ServletOps.errorMethodNotAllowed("PATCH"); }
    @Override protected void doOptions(HttpAction action) { ServletOps.errorMethodNotAllowed("OPTIONS"); }
}

record GraphRAGIndexRequest(String title, String content, String sourceUri) {}

final class GraphRAGIndexingService {
    private final DatasetGraph datasetGraph;
    private final GraphRAGTaskService taskService;
    private final GraphRAGConfiguration configuration;

    GraphRAGIndexingService(DatasetGraph datasetGraph, GraphRAGTaskService taskService,
                            GraphRAGConfiguration configuration) {
        this.datasetGraph = Objects.requireNonNull(datasetGraph);
        this.taskService = Objects.requireNonNull(taskService);
        this.configuration = Objects.requireNonNull(configuration);
    }

    GraphRAGTask submit(GraphRAGIndexRequest request) {
        validate(request);
        GraphRAGTask task = taskService.createTask();
        CompletableFuture.runAsync(() -> execute(task.taskId(), request));
        return task;
    }

    private void validate(GraphRAGIndexRequest request) {
        if ( request.content().length() > configuration.maxIndexContentLength() )
            throw new GraphRAGBadRequestException("content_too_large",
                    "contenu trop volumineux: maximum " + configuration.maxIndexContentLength() + " caracteres");
        try {
            URI uri = new URI(request.sourceUri());
            if ( !uri.isAbsolute() )
                throw new URISyntaxException(request.sourceUri(), "URI absolue requise");
        } catch (URISyntaxException ex) {
            throw new GraphRAGBadRequestException("invalid_request", "sourceUri doit etre une URI absolue valide");
        }
    }

    private void execute(String taskId, GraphRAGIndexRequest request) {
        taskService.markRunning(taskId);
        try {
            index(request, taskId);
            taskService.markDone(taskId);
        } catch (RuntimeException ex) {
            taskService.markFailed(taskId, "echec indexation GraphRAG");
        }
    }

    private void index(GraphRAGIndexRequest request, String taskId) {
        datasetGraph.begin(ReadWrite.WRITE);
        boolean committed = false;
        try {
            Dataset dataset = DatasetFactory.wrap(datasetGraph);
            Model model = dataset.getDefaultModel();
            Resource document = model.createResource(request.sourceUri());
            document.addProperty(RDF.type, GRAG.Document)
                    .addProperty(GRAG.title, request.title())
                    .addProperty(GRAG.sourceFile, request.sourceUri());
            Resource chunk = model.createResource(request.sourceUri() + "#chunk-" + taskId);
            chunk.addProperty(RDF.type, GRAG.Chunk)
                 .addProperty(GRAG.text, request.content())
                 .addLiteral(GRAG.chunkIndex, 0)
                 .addProperty(GRAG.partOf, document);
            datasetGraph.commit();
            committed = true;
        } finally {
            if ( !committed && datasetGraph.supportsTransactionAbort() )
                datasetGraph.abort();
            datasetGraph.end();
        }
    }
}

final class GraphRAGBadRequestException extends RuntimeException {
    private final String code;

    GraphRAGBadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}

final class GraphRAGHttpJson {
    private GraphRAGHttpJson() {}

    static void writeError(HttpAction action, int status, String code, String message) {
        JsonBuilder builder = new JsonBuilder();
        builder.startObject()
               .key("error").startObject()
               .pair("code", code)
               .pair("message", message)
               .finishObject()
               .finishObject();
        writeJson(action, builder.build(), status);
    }

    static void writeJson(HttpAction action, JsonValue json, int status) {
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
}