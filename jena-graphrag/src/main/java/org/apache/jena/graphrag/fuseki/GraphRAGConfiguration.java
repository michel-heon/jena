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

record GraphRAGConfiguration(String defaultMode, int defaultTopK, int maxTopK) {

    static final String DEFAULT_MODE_PROPERTY = "jena.graphrag.defaultMode";
    static final String DEFAULT_TOP_K_PROPERTY = "jena.graphrag.defaultTopK";
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

    static GraphRAGConfiguration fromSystemProperties() {
        String defaultMode = System.getProperty(DEFAULT_MODE_PROPERTY, FALLBACK_MODE);
        int defaultTopK = Integer.getInteger(DEFAULT_TOP_K_PROPERTY, FALLBACK_DEFAULT_TOP_K);
        int maxTopK = Integer.getInteger(MAX_TOP_K_PROPERTY, FALLBACK_MAX_TOP_K);
        return new GraphRAGConfiguration(defaultMode, defaultTopK, maxTopK);
    }
}