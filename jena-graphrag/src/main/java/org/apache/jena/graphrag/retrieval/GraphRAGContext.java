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
 * Cited GraphRAG context returned by the retrieval service.
 * <p>
 * Instances are DTOs for the Java API and the Fuseki JSON response. They are
 * produced from RDF data already present in the dataset and do not imply an LLM
 * call or a network request.
 *
 * @param query the caller query after request validation
 * @param mode retrieval mode used to produce the context
 * @param results context items ordered by the retrieval service
 */
public record GraphRAGContext(String query, String mode, List<Result> results) {

    /**
     * One cited context item.
     * <p>
     * Basic results cite {@code mg:Chunk}; local results cite {@code mg:Relationship};
     * global results cite {@code mg:Community}. Optional fields are {@code null}
     * when they do not apply to the selected retrieval mode.
     *
     * @param uri URI of the cited RDF resource
     * @param score ranking score
     * @param sourceText cited text when available, otherwise an empty string
     * @param type result kind exposed to clients
     * @param entityUri URI of the matched source entity
     * @param entityName display name of the matched source entity
     * @param neighborUri URI of the related target entity
     * @param neighborName display name of the related target entity
     * @param weight relationship weight literal, or {@code null}
     * @param rank relationship rank literal, or {@code null}
     * @param communityUri URI of the matched community, or {@code null}
     * @param communityTitle title of the matched community, or {@code null}
     * @param chunkUri URI of the matched chunk, or {@code null}
     * @param chunkText text of the matched chunk, or {@code null}
     * @param documentUri URI of the source document, or {@code null}
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
            Integer rank,
            String communityUri,
            String communityTitle,
            String chunkUri,
            String chunkText,
            String documentUri) {

        public static Result relationship(String uri, double score, String sourceText,
                String entityUri, String entityName, String neighborUri, String neighborName,
                Double weight, Integer rank) {
            return relationship(uri, score, sourceText, entityUri, entityName, neighborUri, neighborName,
                    weight, rank, null, null, null);
        }

        public static Result relationship(String uri, double score, String sourceText,
                String entityUri, String entityName, String neighborUri, String neighborName,
                Double weight, Integer rank, String chunkUri, String chunkText, String documentUri) {
            return new Result(uri, score, sourceText, "relationship", entityUri, entityName,
                    neighborUri, neighborName, weight, rank, null, null, chunkUri, chunkText, documentUri);
        }

        public static Result community(String uri, double score, String sourceText, String communityTitle) {
            return new Result(uri, score, sourceText, "community", null, null, null, null,
                    null, null, uri, communityTitle, null, null, null);
        }

        public static Result chunk(String uri, double score, String sourceText, String documentUri) {
            return new Result(uri, score, sourceText, "chunk", null, null, null, null,
                    null, null, null, null, uri, sourceText, documentUri);
        }
    }
}