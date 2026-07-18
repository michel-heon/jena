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

/**
 * Runtime limits for the experimental GraphRAG Fuseki endpoint.
 * <p>
 * The delivered endpoint supports only the {@code local} mode. Instances are
 * validated eagerly so invalid system properties fail before request handling.
 *
 * @param defaultMode default retrieval mode exposed by {@code /graphrag/context}
 * @param defaultTopK default maximum number of context results
 * @param maxTopK hard upper bound accepted from HTTP requests
 */
record GraphRAGConfiguration(String defaultMode, int defaultTopK, int maxTopK) {

    /** System property overriding the default retrieval mode. */
    static final String DEFAULT_MODE_PROPERTY = "jena.graphrag.defaultMode";
    /** System property overriding the default {@code topK} value. */
    static final String DEFAULT_TOP_K_PROPERTY = "jena.graphrag.defaultTopK";
    /** System property overriding the maximum accepted {@code topK} value. */
    static final String MAX_TOP_K_PROPERTY = "jena.graphrag.maxTopK";

    private static final String FALLBACK_MODE = "local";
    private static final int FALLBACK_DEFAULT_TOP_K = 5;
    private static final int FALLBACK_MAX_TOP_K = 100;

    GraphRAGConfiguration {
        if ( !FALLBACK_MODE.equals(defaultMode) )
            throw new IllegalArgumentException("defaultMode invalide: " + defaultMode);
        if ( defaultTopK < 1 )
            throw new IllegalArgumentException("defaultTopK doit etre positif");
        if ( maxTopK < defaultTopK )
            throw new IllegalArgumentException("maxTopK doit etre superieur ou egal a defaultTopK");
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
        return new GraphRAGConfiguration(defaultMode, defaultTopK, maxTopK);
    }
}