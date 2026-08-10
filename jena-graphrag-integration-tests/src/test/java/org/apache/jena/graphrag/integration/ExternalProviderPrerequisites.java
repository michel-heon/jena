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

package org.apache.jena.graphrag.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.jena.graphrag.index.GraphRAGAssemblerVocab;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Checks the environment-variable contract declared by a real GraphRAG provider configuration.
 * Values are intentionally never included in a diagnostic.
 */
public final class ExternalProviderPrerequisites {

    static final String EMBEDDING_ENDPOINT = "GRAPHRAG_EMBEDDING_API_URL";
    static final String EMBEDDING_API_KEY = "GRAPHRAG_EMBEDDING_API_KEY";
    static final String EMBEDDING_MODEL = "GRAPHRAG_EMBEDDING_MODEL";
    static final String EMBEDDING_DIMENSION = "GRAPHRAG_EMBEDDING_DIMENSION";
    static final String CHAT_ENDPOINT = "OPENAI_API_URL";
    static final String CHAT_API_KEY = "OPENAI_API_KEY";
    static final String CHAT_MODEL = "GRAPHRAG_CHAT_MODEL";

    private ExternalProviderPrerequisites() {}

    /**
     * Fails before a real provider call when configured credential or endpoint variables are absent.
     *
     * @param provider provider resource containing {@code grag:apiKeyEnv} and optionally {@code grag:endpointEnv}
     * @param environment environment to inspect, normally {@link System#getenv()}
     * @throws IllegalStateException when one or more configured variables are missing or blank
     */
    public static void requireConfiguredEnvironment(Resource provider, Map<String, String> environment) {
        List<String> missing = new ArrayList<>();
        requireIfConfigured(provider, GraphRAGAssemblerVocab.apiKeyEnv, environment, missing);
        requireIfConfigured(provider, GraphRAGAssemblerVocab.endpointEnv, environment, missing);
        if ( !missing.isEmpty() )
            throw new IllegalStateException("Required provider environment variables are not set: "
                    + String.join(", ", missing));
    }

    /** Fails before the real-provider Fuseki suite can start when its environment is incomplete. */
    public static void requireRealProviderEnvironment(Map<String, String> environment) {
        List<String> missing = new ArrayList<>();
        for ( String variable : List.of(EMBEDDING_ENDPOINT, EMBEDDING_API_KEY, EMBEDDING_MODEL, EMBEDDING_DIMENSION,
                CHAT_ENDPOINT, CHAT_API_KEY, CHAT_MODEL) ) {
            String value = environment.get(variable);
            if ( value == null || value.isBlank() )
                missing.add(variable);
        }
        if ( !missing.isEmpty() )
            throw new IllegalStateException("Required real-provider environment variables are not set: "
                    + String.join(", ", missing));
    }

    private static void requireIfConfigured(Resource provider, Property property, Map<String, String> environment,
                                            List<String> missing) {
        if ( !provider.hasProperty(property) )
            return;
        String variableName = provider.getRequiredProperty(property).getString();
        String value = environment.get(variableName);
        if ( value == null || value.isBlank() )
            missing.add(variableName);
    }
}