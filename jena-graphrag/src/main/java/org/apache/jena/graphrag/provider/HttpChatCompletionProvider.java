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
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.langchain4j.model.openai.OpenAiChatModel;

/** OpenAI-compatible chat provider, disabled unless external calls are explicitly allowed. */
public final class HttpChatCompletionProvider implements ChatCompletionProvider {
    private static final String AZURE_OPENAI_HOST = ".openai.azure.com";

    private final ProviderConfiguration configuration;
    private final OpenAiChatModel model;

    public HttpChatCompletionProvider(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if ( !configuration.allowExternalCalls() )
            throw new ProviderException("External provider calls are disabled");

        URI checkedEndpoint = requireHttpEndpoint(endpoint);
        String checkedModel = requireNonBlank(model, "model");
        String checkedApiKey = requireNonBlank(apiKey, "apiKey");
        URI normalizedEndpoint = normalizeChatEndpoint(checkedEndpoint);
        Map<String, String> customQueryParams = extractQueryParams(checkedEndpoint);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
            .baseUrl(normalizedEndpoint.toString())
                .modelName(checkedModel)
                .apiKey(checkedApiKey)
                .maxTokens(configuration.maxTokensPerRequest())
                .timeout(configuration.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);

        if ( !customQueryParams.isEmpty() )
            builder.customQueryParams(customQueryParams);

        if ( usesAzureApiKey(checkedEndpoint) )
            builder.customHeaders(Map.of("api-key", checkedApiKey));

        this.model = builder.build();
    }

    @Override
    public String complete(String question, List<String> contextPassages) {
        String prompt = "Question:\n" + question + "\n\nContext:\n" + String.join("\n\n", contextPassages);
        checkInputQuota(prompt);
        try {
            String answer = model.chat(prompt);
            if ( answer == null )
                throw new ProviderException("Provider returned an invalid chat response");
            return answer;
        } catch (ProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProviderException(sanitizeFailure(ex), ex);
        }
    }

    private void checkInputQuota(String input) {
        String stripped = Objects.requireNonNull(input, "input").strip();
        int estimatedTokens = stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
        if ( estimatedTokens > configuration.maxTokensPerRequest() )
            throw new ProviderQuotaExceededException(configuration.maxTokensPerRequest());
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

    private static URI normalizeChatEndpoint(URI endpoint) {
        String path = endpoint.getPath();
        if ( path == null || path.isBlank() )
            return endpoint;

        String normalizedPath = path;
        if ( normalizedPath.endsWith("/chat/completions") )
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - "/chat/completions".length());

        try {
            return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(),
                    normalizedPath, null, endpoint.getFragment());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("endpoint must be a valid HTTP(S) URI", ex);
        }
    }

    private static Map<String, String> extractQueryParams(URI endpoint) {
        String query = endpoint.getRawQuery();
        if ( query == null || query.isBlank() )
            return Map.of();

        return java.util.Arrays.stream(query.split("&"))
                .map(HttpChatCompletionProvider::splitQueryParam)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(parts -> decodeQueryComponent(parts[0]),
                        parts -> decodeQueryComponent(parts[1]), (left, right) -> right));
    }

    private static String[] splitQueryParam(String entry) {
        int separator = entry.indexOf('=');
        if ( separator < 0 )
            return new String[] { entry, "" };
        return new String[] { entry.substring(0, separator), entry.substring(separator + 1) };
    }

    private static String decodeQueryComponent(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}