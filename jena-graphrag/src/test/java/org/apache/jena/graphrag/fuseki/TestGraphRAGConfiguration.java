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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGConfiguration {

    @Test
    public void systemPropertiesProvideDefaults() {
        GraphRAGConfiguration configuration = GraphRAGConfiguration.fromSystemProperties();

        assertEquals("local", configuration.defaultMode());
        assertTrue(configuration.defaultTopK() > 0);
        assertTrue(configuration.maxTopK() >= configuration.defaultTopK());
        assertEquals(0.5, configuration.hybridAlpha());
        assertTrue(configuration.maxIndexContentLength() > 0);
        assertTrue(configuration.maxActiveTasks() > 0);
        assertTrue(configuration.maxRetainedCompletedTasks() > 0);
    }

    @Test
    public void acceptsGlobalDefaultMode() {
        GraphRAGConfiguration configuration = new GraphRAGConfiguration("global", 5, 100, 0.5);

        assertEquals("global", configuration.defaultMode());
    }

    @Test
    public void acceptsBasicDefaultMode() {
        GraphRAGConfiguration configuration = new GraphRAGConfiguration("basic", 5, 100, 0.5);

        assertEquals("basic", configuration.defaultMode());
    }

    @Test
    public void rejectsUnsupportedDefaultMode() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("drift", 5, 100, 0.5));
    }

    @Test
    public void rejectsInvalidTopKBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 0, 100, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 10, 5, 0.5));
        }

        @Test
        public void rejectsInvalidHybridAlpha() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, -0.1));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, 1.1));
    }

        @Test
        public void rejectsInvalidPhase4Limits() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, 0.5, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, 0.5, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, 0.5, 1, 1, 0));
    }

        @Test
        public void modelOverridesHybridAlpha() {
            Model config = ModelFactory.createDefaultModel();
            config.createResource("urn:graphrag:index")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "hybridAlpha"), 0.25);

            GraphRAGConfiguration configuration = GraphRAGConfiguration.fromModel(config);

            assertEquals(0.25, configuration.hybridAlpha());
        }

            @Test
            public void taskLifecycleTracksPendingRunningDone() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 2, 10);

                GraphRAGTask created = service.createTask();
                GraphRAGTask running = service.markRunning(created.taskId());
                GraphRAGTask done = service.markDone(created.taskId());

                assertEquals("graphrag-1", created.taskId());
                assertEquals(GraphRAGTaskStatus.PENDING, created.status());
                assertEquals(GraphRAGTaskStatus.RUNNING, running.status());
                assertEquals(GraphRAGTaskStatus.DONE, done.status());
                assertNotNull(done.startedAt());
                assertNotNull(done.completedAt());
                assertEquals(done, service.find(created.taskId()).orElseThrow());
            }

            @Test
            public void unknownTaskRaisesExplicitException() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 2, 10);

                assertThrows(GraphRAGTaskService.UnknownTaskException.class, () -> service.markDone("missing"));
                assertTrue(service.find("missing").isEmpty());
            }

            @Test
            public void failedTaskKeepsOperatorSafeErrorMessage() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 2, 10);
                GraphRAGTask task = service.createTask();

                GraphRAGTask failed = service.markFailed(task.taskId(), "contenu invalide");

                assertEquals(GraphRAGTaskStatus.FAILED, failed.status());
                assertEquals("contenu invalide", failed.error());
                assertNotNull(failed.completedAt());
            }

            @Test
            public void activeTaskLimitRejectsUnboundedQueue() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 1, 10);

                service.createTask();

                assertThrows(GraphRAGTaskService.TaskLimitExceededException.class, service::createTask);
            }

            @Test
            public void summaryCountsTodayAndLastSuccess() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 3, 10);
                GraphRAGTask done = service.createTask();
                GraphRAGTask failed = service.createTask();
                service.createTask();

                service.markDone(done.taskId());
                service.markFailed(failed.taskId(), "erreur");
                GraphRAGTaskSummary summary = service.summary();

                assertEquals(1, summary.activeTasks());
                assertEquals(1, summary.completedToday());
                assertEquals(1, summary.failedToday());
                assertEquals(Instant.parse("2026-07-19T10:00:00Z"), summary.lastSuccess());
            }

            @Test
            public void completedTaskRetentionKeepsRecentTasks() {
                GraphRAGTaskService service = serviceAt("2026-07-19T10:00:00Z", 2, 1);
                GraphRAGTask oldTask = service.createTask();
                service.markDone(oldTask.taskId());
                GraphRAGTask recentTask = service.createTask();

                service.markDone(recentTask.taskId());

                assertTrue(service.find(oldTask.taskId()).isEmpty());
                assertTrue(service.find(recentTask.taskId()).isPresent());
            }

            private static GraphRAGTaskService serviceAt(String instant, int maxActiveTasks, int maxRetainedCompletedTasks) {
                return new GraphRAGTaskService(maxActiveTasks, maxRetainedCompletedTasks,
                        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
            }
}