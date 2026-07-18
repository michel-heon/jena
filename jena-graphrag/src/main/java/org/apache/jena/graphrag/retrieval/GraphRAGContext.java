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

import java.util.List;

/**
 * Cited GraphRAG context returned by the local retrieval service.
 * <p>
 * Instances are DTOs for the Java API and the Fuseki JSON response. They are
 * produced from RDF data already present in the dataset and do not imply an LLM
 * call or a network request.
 *
 * @param query the caller query after request validation
 * @param mode retrieval mode used to produce the context; currently {@code local}
 * @param results relationship-backed context items ordered by the retrieval service
 */
public record GraphRAGContext(String query, String mode, List<Result> results) {

    /**
     * One relationship-backed item of local context.
     * <p>
     * The URI identifies the {@code mg:Relationship} resource. Optional numeric
     * fields are {@code null} when the corresponding RDF literal is absent.
     *
     * @param uri URI of the relationship resource used as citation anchor
     * @param score ranking score derived from {@code mg:weight}, {@code mg:rank}, or a fallback
     * @param sourceText relationship description when available, otherwise an empty string
     * @param type result kind exposed to clients; currently {@code relationship}
     * @param entityUri URI of the matched source entity
     * @param entityName display name of the matched source entity
     * @param neighborUri URI of the related target entity
     * @param neighborName display name of the related target entity
     * @param weight relationship weight literal, or {@code null}
     * @param rank relationship rank literal, or {@code null}
     */
    public record Result(
            String uri,
            double score,
            String sourceText,
            String type,
            String entityUri,
            String entityName,
            String neighborUri,
            String neighborName,
            Double weight,
            Integer rank) {}
}