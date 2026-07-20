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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.jena.graphrag.retrieval.GraphRAGContextService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.StmtIterator;

/**
 * Runtime limits for the experimental GraphRAG Fuseki endpoint.
 * <p>
 * The delivered endpoint supports the {@code basic}, {@code local} and
 * {@code global} modes.
 * Instances are validated eagerly so invalid system properties fail before
 * request handling.
 *
 * @param defaultMode default retrieval mode exposed by {@code /graphrag/context}
 * @param defaultTopK default maximum number of context results
 * @param maxTopK hard upper bound accepted from HTTP requests
 * @param hybridAlpha text score weight used by hybrid retrieval ranking
 * @param maxIndexContentLength maximum accepted {@code /graphrag/index} content length
 * @param maxActiveTasks maximum number of concurrently active GraphRAG tasks
 * @param maxRetainedCompletedTasks maximum number of completed task records retained in memory
 */
record GraphRAGConfiguration(String defaultMode, int defaultTopK, int maxTopK, double hybridAlpha,
                             int maxIndexContentLength, int maxActiveTasks, int maxRetainedCompletedTasks) {

    /** System property overriding the default retrieval mode. */
    static final String DEFAULT_MODE_PROPERTY = "jena.graphrag.defaultMode";
    /** System property overriding the default {@code topK} value. */
    static final String DEFAULT_TOP_K_PROPERTY = "jena.graphrag.defaultTopK";
    /** System property overriding the maximum accepted {@code topK} value. */
    static final String MAX_TOP_K_PROPERTY = "jena.graphrag.maxTopK";
    /** System property overriding {@code grag:hybridAlpha}. */
    static final String HYBRID_ALPHA_PROPERTY = "jena.graphrag.hybridAlpha";
    /** System property overriding maximum accepted indexing content length. */
    static final String MAX_INDEX_CONTENT_LENGTH_PROPERTY = "jena.graphrag.index.maxContentLength";
    /** System property overriding maximum active GraphRAG tasks. */
    static final String MAX_ACTIVE_TASKS_PROPERTY = "jena.graphrag.tasks.maxActive";
    /** System property overriding completed GraphRAG task retention. */
    static final String MAX_RETAINED_COMPLETED_TASKS_PROPERTY = "jena.graphrag.tasks.maxRetainedCompleted";

    private static final String FALLBACK_MODE = GraphRAGContextService.LOCAL_MODE;
    private static final int FALLBACK_DEFAULT_TOP_K = 5;
    private static final int FALLBACK_MAX_TOP_K = 100;
    private static final double FALLBACK_HYBRID_ALPHA = 0.5;
    private static final int FALLBACK_MAX_INDEX_CONTENT_LENGTH = 1_000_000;
    private static final int FALLBACK_MAX_ACTIVE_TASKS = 2;
    private static final int FALLBACK_MAX_RETAINED_COMPLETED_TASKS = 100;

    GraphRAGConfiguration(String defaultMode, int defaultTopK, int maxTopK, double hybridAlpha) {
        this(defaultMode, defaultTopK, maxTopK, hybridAlpha, FALLBACK_MAX_INDEX_CONTENT_LENGTH,
                FALLBACK_MAX_ACTIVE_TASKS, FALLBACK_MAX_RETAINED_COMPLETED_TASKS);
    }

    GraphRAGConfiguration {
        if ( !GraphRAGContextService.supportsMode(defaultMode) )
            throw new IllegalArgumentException("defaultMode invalide: " + defaultMode);
        if ( defaultTopK < 1 )
            throw new IllegalArgumentException("defaultTopK doit etre positif");
        if ( maxTopK < defaultTopK )
            throw new IllegalArgumentException("maxTopK doit etre superieur ou egal a defaultTopK");
        if ( hybridAlpha < 0.0 || hybridAlpha > 1.0 )
            throw new IllegalArgumentException("hybridAlpha doit etre compris entre 0.0 et 1.0");
        if ( maxIndexContentLength < 1 )
            throw new IllegalArgumentException("maxIndexContentLength doit etre positif");
        if ( maxActiveTasks < 1 )
            throw new IllegalArgumentException("maxActiveTasks doit etre positif");
        if ( maxRetainedCompletedTasks < 1 )
            throw new IllegalArgumentException("maxRetainedCompletedTasks doit etre positif");
    }

    /**
     * Loads endpoint limits from JVM system properties, using safe local
     * defaults when a property is absent.
     *
     * @return validated endpoint configuration
     * @throws IllegalArgumentException if any configured value is outside the delivered contract
     */
    static GraphRAGConfiguration fromSystemProperties() {
        String defaultMode = System.getProperty(DEFAULT_MODE_PROPERTY, FALLBACK_MODE);
        int defaultTopK = Integer.getInteger(DEFAULT_TOP_K_PROPERTY, FALLBACK_DEFAULT_TOP_K);
        int maxTopK = Integer.getInteger(MAX_TOP_K_PROPERTY, FALLBACK_MAX_TOP_K);
        double hybridAlpha = Double.parseDouble(System.getProperty(HYBRID_ALPHA_PROPERTY,
            Double.toString(FALLBACK_HYBRID_ALPHA)));
        int maxIndexContentLength = Integer.getInteger(MAX_INDEX_CONTENT_LENGTH_PROPERTY, FALLBACK_MAX_INDEX_CONTENT_LENGTH);
        int maxActiveTasks = Integer.getInteger(MAX_ACTIVE_TASKS_PROPERTY, FALLBACK_MAX_ACTIVE_TASKS);
        int maxRetainedCompletedTasks = Integer.getInteger(MAX_RETAINED_COMPLETED_TASKS_PROPERTY,
            FALLBACK_MAX_RETAINED_COMPLETED_TASKS);
        return new GraphRAGConfiguration(defaultMode, defaultTopK, maxTopK, hybridAlpha,
            maxIndexContentLength, maxActiveTasks, maxRetainedCompletedTasks);
    }

    /**
     * Loads endpoint configuration, overriding {@code hybridAlpha} when the
     * Fuseki configuration model contains {@code grag:hybridAlpha}.
     *
     * @param configModel Fuseki configuration model, or {@code null}
     * @return validated endpoint configuration
     */
    static GraphRAGConfiguration fromModel(Model configModel) {
        GraphRAGConfiguration fallback = fromSystemProperties();
        if ( configModel == null )
            return fallback;

        Property hybridAlpha = configModel.createProperty(GraphRAGModule.CONFIG_NS + "hybridAlpha");
        StmtIterator statements = configModel.listStatements(null, hybridAlpha, (RDFNode) null);
        try {
            if ( !statements.hasNext() )
                return fallback;
            RDFNode value = statements.nextStatement().getObject();
            if ( !value.isLiteral() )
                throw new IllegalArgumentException("grag:hybridAlpha doit etre un litteral numerique");
            return new GraphRAGConfiguration(fallback.defaultMode(), fallback.defaultTopK(), fallback.maxTopK(),
                    value.asLiteral().getDouble(), fallback.maxIndexContentLength(), fallback.maxActiveTasks(),
                    fallback.maxRetainedCompletedTasks());
        } finally {
            statements.close();
        }
    }
}

enum GraphRAGTaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed");

    private final String jsonValue;

    GraphRAGTaskStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    String jsonValue() {
        return jsonValue;
    }
}

record GraphRAGTask(String taskId, GraphRAGTaskStatus status, Instant createdAt, Instant startedAt,
                   Instant completedAt, String error) {

    GraphRAGTask {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    GraphRAGTask running(Instant startedAt) {
        return new GraphRAGTask(taskId, GraphRAGTaskStatus.RUNNING, createdAt, Objects.requireNonNull(startedAt), null, null);
    }

    GraphRAGTask done(Instant completedAt) {
        return new GraphRAGTask(taskId, GraphRAGTaskStatus.DONE, createdAt, startedAt, Objects.requireNonNull(completedAt), null);
    }

    GraphRAGTask failed(Instant completedAt, String error) {
        return new GraphRAGTask(taskId, GraphRAGTaskStatus.FAILED, createdAt, startedAt,
                Objects.requireNonNull(completedAt), Objects.requireNonNull(error));
    }
}

record GraphRAGTaskSummary(int activeTasks, int completedToday, int failedToday, Instant lastSuccess) {}

final class GraphRAGTaskService {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, GraphRAGTask> tasks = new LinkedHashMap<>();
    private final int maxActiveTasks;
    private final int maxRetainedCompletedTasks;
    private final Clock clock;

    GraphRAGTaskService(int maxActiveTasks, int maxRetainedCompletedTasks) {
        this(maxActiveTasks, maxRetainedCompletedTasks, Clock.systemUTC());
    }

    GraphRAGTaskService(int maxActiveTasks, int maxRetainedCompletedTasks, Clock clock) {
        if ( maxActiveTasks < 1 )
            throw new IllegalArgumentException("maxActiveTasks doit etre positif");
        if ( maxRetainedCompletedTasks < 1 )
            throw new IllegalArgumentException("maxRetainedCompletedTasks doit etre positif");
        this.maxActiveTasks = maxActiveTasks;
        this.maxRetainedCompletedTasks = maxRetainedCompletedTasks;
        this.clock = Objects.requireNonNull(clock);
    }

    synchronized GraphRAGTask createTask() {
        if ( activeTasks() >= maxActiveTasks )
            throw new TaskLimitExceededException("trop de taches GraphRAG actives");
        String taskId = "graphrag-" + sequence.incrementAndGet();
        GraphRAGTask task = new GraphRAGTask(taskId, GraphRAGTaskStatus.PENDING, clock.instant(), null, null, null);
        tasks.put(taskId, task);
        return task;
    }

    synchronized Optional<GraphRAGTask> find(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    synchronized GraphRAGTask markRunning(String taskId) {
        GraphRAGTask task = requireTask(taskId).running(clock.instant());
        tasks.put(taskId, task);
        return task;
    }

    synchronized GraphRAGTask markDone(String taskId) {
        GraphRAGTask task = requireTask(taskId).done(clock.instant());
        tasks.put(taskId, task);
        pruneCompletedTasks();
        return task;
    }

    synchronized GraphRAGTask markFailed(String taskId, String error) {
        GraphRAGTask task = requireTask(taskId).failed(clock.instant(), error);
        tasks.put(taskId, task);
        pruneCompletedTasks();
        return task;
    }

    synchronized GraphRAGTaskSummary summary() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        int activeTasks = 0;
        int completedToday = 0;
        int failedToday = 0;
        Instant lastSuccess = null;

        for ( GraphRAGTask task : tasks.values() ) {
            if ( task.status() == GraphRAGTaskStatus.PENDING || task.status() == GraphRAGTaskStatus.RUNNING )
                activeTasks++;
            if ( task.completedAt() == null )
                continue;
            LocalDate completedDate = LocalDate.ofInstant(task.completedAt(), zone);
            if ( task.status() == GraphRAGTaskStatus.DONE ) {
                if ( today.equals(completedDate) )
                    completedToday++;
                if ( lastSuccess == null || task.completedAt().isAfter(lastSuccess) )
                    lastSuccess = task.completedAt();
            }
            if ( task.status() == GraphRAGTaskStatus.FAILED && today.equals(completedDate) )
                failedToday++;
        }
        return new GraphRAGTaskSummary(activeTasks, completedToday, failedToday, lastSuccess);
    }

    private int activeTasks() {
        int count = 0;
        for ( GraphRAGTask task : tasks.values() ) {
            if ( task.status() == GraphRAGTaskStatus.PENDING || task.status() == GraphRAGTaskStatus.RUNNING )
                count++;
        }
        return count;
    }

    private GraphRAGTask requireTask(String taskId) {
        GraphRAGTask task = tasks.get(taskId);
        if ( task == null )
            throw new UnknownTaskException(taskId);
        return task;
    }

    private void pruneCompletedTasks() {
        List<String> completedTaskIds = new ArrayList<>();
        for ( GraphRAGTask task : tasks.values() ) {
            if ( task.status() == GraphRAGTaskStatus.DONE || task.status() == GraphRAGTaskStatus.FAILED )
                completedTaskIds.add(task.taskId());
        }
        int removableCount = completedTaskIds.size() - maxRetainedCompletedTasks;
        for ( int index = 0; index < removableCount; index++ )
            tasks.remove(completedTaskIds.get(index));
    }

    static final class UnknownTaskException extends RuntimeException {
        UnknownTaskException(String taskId) {
            super("tache GraphRAG inconnue: " + taskId);
        }
    }

    static final class TaskLimitExceededException extends RuntimeException {
        TaskLimitExceededException(String message) {
            super(message);
        }
    }
}