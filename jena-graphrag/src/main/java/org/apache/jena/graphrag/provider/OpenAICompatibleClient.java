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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonException;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

final class OpenAICompatibleClient {
    private final ProviderConfiguration configuration;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient httpClient;

    OpenAICompatibleClient(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if ( !configuration.allowExternalCalls() )
            throw new ProviderException("External provider calls are disabled");
        this.endpoint = requireHttpEndpoint(endpoint);
        this.model = requireNonBlank(model, "model");
        this.apiKey = requireNonBlank(apiKey, "apiKey");
        this.httpClient = HttpClient.newBuilder().connectTimeout(configuration.timeout()).build();
    }

    String model() {
        return model;
    }

    int maximumTokens() {
        return configuration.maxTokensPerRequest();
    }

    void checkInputQuota(String input) {
        String stripped = Objects.requireNonNull(input, "input").strip();
        int estimatedTokens = stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
        if ( estimatedTokens > maximumTokens() )
            throw new ProviderQuotaExceededException(maximumTokens());
    }

    JsonObject post(JsonValue requestBody) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(configuration.timeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if ( response.statusCode() < 200 || response.statusCode() >= 300 )
                throw new ProviderException("Provider returned HTTP " + response.statusCode());
            try {
                return JSON.parse(response.body());
            } catch (JsonException | ClassCastException ex) {
                throw new ProviderException("Provider returned invalid JSON");
            }
        } catch (IOException ex) {
            throw new ProviderException("Provider request failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException("Provider request interrupted");
        }
    }

    private static URI requireHttpEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if ( !endpoint.isAbsolute() || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme())) )
            throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URI");
        return endpoint;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if ( value.isBlank() )
            throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}