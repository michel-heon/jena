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

package org.apache.jena.graphrag.provider;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import org.apache.jena.graphrag.index.EmbeddingProvider;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/** OpenAI-compatible embedding provider, disabled unless external calls are explicitly allowed. */
public final class HttpEmbeddingProvider implements EmbeddingProvider {
    private static final String AZURE_OPENAI_HOST = ".openai.azure.com";

    private final ProviderConfiguration configuration;
    private final URI endpoint;
    private final String modelName;
    private final String apiKey;

    public HttpEmbeddingProvider(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if ( !configuration.allowExternalCalls() )
            throw new ProviderException("External provider calls are disabled");

        this.endpoint = requireHttpEndpoint(endpoint);
        this.modelName = requireNonBlank(model, "model");
        this.apiKey = requireNonBlank(apiKey, "apiKey");
    }

    private OpenAiEmbeddingModel createModel(int dimension) {
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(endpoint.toString())
                .modelName(modelName)
                .apiKey(apiKey)
                .dimensions(dimension)
                .timeout(configuration.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);

        if ( usesAzureApiKey(endpoint) )
            builder.customHeaders(Map.of("api-key", apiKey));

        return builder.build();
    }

    @Override
    public float[] embed(String text, int dimension) {
        checkInputQuota(text);
        try {
            Embedding embedding = createModel(dimension).embed(text).content();
            float[] vector = embedding.vector();
            if ( vector.length != dimension )
                throw new ProviderException("Provider returned an unexpected embedding dimension");
            return vector;
        } catch (ProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ProviderException.from(ex);
        }
    }

    private int maximumTokens() {
        return configuration.maxTokensPerRequest();
    }

    private void checkInputQuota(String input) {
        String stripped = Objects.requireNonNull(input, "input").strip();
        int estimatedTokens = stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
        if ( estimatedTokens > maximumTokens() )
            throw new ProviderQuotaExceededException(maximumTokens());
    }

    private static URI requireHttpEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if ( !endpoint.isAbsolute() || !(
                "http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme())) )
            throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URI");
        return endpoint;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if ( value.isBlank() )
            throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static boolean usesAzureApiKey(URI endpoint) {
        String host = endpoint.getHost();
        return host != null && host.endsWith(AZURE_OPENAI_HOST);
    }
}