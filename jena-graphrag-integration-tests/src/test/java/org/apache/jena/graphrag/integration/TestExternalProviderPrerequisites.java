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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.apache.jena.graphrag.index.GraphRAGAssemblerVocab;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

public class TestExternalProviderPrerequisites {

    @Test
    public void missingConfiguredVariablesFailWithoutPrintingValues() {
        Resource provider = providerWithEnvironmentVariables();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ExternalProviderPrerequisites.requireConfiguredEnvironment(provider, Map.of()));

        assertEquals("Required provider environment variables are not set: "
                + "GRAPHRAG_IT_API_KEY, GRAPHRAG_IT_ENDPOINT", error.getMessage());
    }

    @Test
    public void configuredVariablesPermitTheRealProviderSuiteToStart() {
        Resource provider = providerWithEnvironmentVariables();

        assertDoesNotThrow(() -> ExternalProviderPrerequisites.requireConfiguredEnvironment(provider,
                Map.of("GRAPHRAG_IT_API_KEY", "present", "GRAPHRAG_IT_ENDPOINT", "https://example.test/")));
    }

        @Test
        public void incompleteRealProviderEnvironmentFailsWithoutValues() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> ExternalProviderPrerequisites.requireRealProviderEnvironment(Map.of()));

        assertEquals("Required real-provider environment variables are not set: "
            + "GRAPHRAG_EMBEDDING_API_URL, GRAPHRAG_EMBEDDING_API_KEY, GRAPHRAG_EMBEDDING_MODEL, "
            + "GRAPHRAG_EMBEDDING_DIMENSION, OPENAI_API_URL, OPENAI_API_KEY, "
            + "GRAPHRAG_CHAT_MODEL", error.getMessage());
        }

    private static Resource providerWithEnvironmentVariables() {
        Model model = ModelFactory.createDefaultModel();
        return model.createResource()
                .addProperty(GraphRAGAssemblerVocab.apiKeyEnv, "GRAPHRAG_IT_API_KEY")
                .addProperty(GraphRAGAssemblerVocab.endpointEnv, "GRAPHRAG_IT_ENDPOINT");
    }
}