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
    private final OpenAiEmbeddingModel model;

    public HttpEmbeddingProvider(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if ( !configuration.allowExternalCalls() )
            throw new ProviderException("External provider calls are disabled");

        URI checkedEndpoint = requireHttpEndpoint(endpoint);
        String checkedModel = requireNonBlank(model, "model");
        String checkedApiKey = requireNonBlank(apiKey, "apiKey");

        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(checkedEndpoint.toString())
                .modelName(checkedModel)
                .apiKey(checkedApiKey)
                .timeout(configuration.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);

        if ( usesAzureApiKey(checkedEndpoint) )
            builder.customHeaders(Map.of("api-key", checkedApiKey));

        this.model = builder.build();
    }

    @Override
    public float[] embed(String text, int dimension) {
        checkInputQuota(text);
        try {
            Embedding embedding = model.embed(text).content();
            float[] vector = embedding.vector();
            if ( vector.length != dimension )
                throw new ProviderException("Provider returned an unexpected embedding dimension");
            return vector;
        } catch (ProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProviderException(sanitizeFailure(ex), ex);
        }
    }

    private static String sanitizeFailure(RuntimeException ex) {
        String message = ex.getMessage();
        String exceptionType = ex.getClass().getSimpleName();
        if ( message == null || message.isBlank() )
            return "Provider request failed (" + exceptionType + ")";
        if ( message.contains("invalid json") || message.contains("Invalid JSON") )
            return "Provider returned invalid JSON";
        if ( message.contains("404") || message.contains("Not Found") )
            return "Provider endpoint rejected the request (404/" + exceptionType + ")";
        if ( message.contains("401") || message.contains("403") || message.contains("Unauthorized") || message.contains("Forbidden") )
            return "Provider authentication failed (" + extractStatusCode(message) + "/" + exceptionType + ")";
        if ( message.contains("400") || message.contains("Bad Request") )
            return "Provider rejected the request payload (400/" + exceptionType + ")";
        if ( message.contains("status code") || message.contains("HTTP") )
            return "Provider request failed (" + extractStatusCode(message) + "/" + exceptionType + ")";
        return "Provider request failed (" + exceptionType + ")";
    }

    private static String extractStatusCode(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(400|401|403|404|429|500|502|503|504)\\b").matcher(message);
        return matcher.find() ? matcher.group(1) : "unknown";
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