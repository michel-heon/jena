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

package org.apache.jena.graphrag.index;

import java.net.URI;
import java.time.Duration;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.Mode;
import org.apache.jena.assembler.assemblers.AssemblerBase;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.graphrag.provider.HttpChatCompletionProvider;
import org.apache.jena.graphrag.provider.HttpEmbeddingProvider;
import org.apache.jena.graphrag.provider.ProviderConfiguration;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

final class GraphRAGHttpProviderAssembler extends AssemblerBase {
    private final boolean embedding;

    GraphRAGHttpProviderAssembler(boolean embedding) {
        this.embedding = embedding;
    }

    @Override
    public Object open(Assembler assembler, Resource root, Mode mode) {
        boolean allowed = booleanValue(root, GraphRAGAssemblerVocab.allowExternalCalls, false);
        if ( !allowed )
            throw new AssemblerException(root, "External provider calls require grag:allowExternalCalls true");
        URI endpoint = URI.create(requiredString(root, GraphRAGAssemblerVocab.endpoint));
        String model = requiredString(root, GraphRAGAssemblerVocab.modelName);
        String environmentName = requiredString(root, GraphRAGAssemblerVocab.apiKeyEnv);
        String apiKey = System.getenv(environmentName);
        if ( apiKey == null || apiKey.isBlank() )
            throw new AssemblerException(root, "Required provider environment variable is not set: " + environmentName);
        int timeout = intValue(root, GraphRAGAssemblerVocab.timeoutSeconds, 30);
        int maximumTokens = intValue(root, GraphRAGAssemblerVocab.maxTokensPerRequest, 4096);
        ProviderConfiguration configuration = new ProviderConfiguration(true, Duration.ofSeconds(timeout), maximumTokens);
        if ( embedding )
            return new HttpEmbeddingProvider(configuration, endpoint, model, apiKey);
        return new HttpChatCompletionProvider(configuration, endpoint, model, apiKey);
    }

    private static String requiredString(Resource root, Property property) {
        RDFNode value = root.getProperty(property) == null ? null : root.getProperty(property).getObject();
        if ( value == null || !value.isLiteral() || value.asLiteral().getString().isBlank() )
            throw new AssemblerException(root, "Required non-blank literal missing: " + property);
        return value.asLiteral().getString();
    }

    private static boolean booleanValue(Resource root, Property property, boolean fallback) {
        if ( !root.hasProperty(property) )
            return fallback;
        try {
            return root.getProperty(property).getBoolean();
        } catch (RuntimeException ex) {
            throw new AssemblerException(root, "Property must be a boolean literal: " + property, ex);
        }
    }

    private static int intValue(Resource root, Property property, int fallback) {
        if ( !root.hasProperty(property) )
            return fallback;
        try {
            int value = root.getProperty(property).getInt();
            if ( value < 1 )
                throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException ex) {
            throw new AssemblerException(root, "Property must be a positive integer: " + property, ex);
        }
    }
}