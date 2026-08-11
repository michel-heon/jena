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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.junit.jupiter.api.Test;

public class TestHttpProviders {
    private static final String API_KEY = "sk-test-provider-secret";

    @Test
    public void externalCallsDisabled_refusesProviderBeforeRequest() {
        ProviderException exception = assertThrows(ProviderException.class,
                () -> new HttpEmbeddingProvider(ProviderConfiguration.localDefaults(), URI.create("http://127.0.0.1:1"),
                        "model", API_KEY));

        assertFalse(exception.toString().contains(API_KEY));
    }

    @Test
    public void embeddingProvider_callsLoopbackAndParsesVector() throws Exception {
        try (TestServer server = TestServer.responding(200, "{\"data\":[{\"embedding\":[0.25,0.75]}]}")) {
            HttpEmbeddingProvider provider = new HttpEmbeddingProvider(configuration(Duration.ofSeconds(2), 10),
                    server.uri(), "embedding-model", API_KEY);

            assertArrayEquals(new float[] { 0.25f, 0.75f }, provider.embed("hello world", 2));
            assertEquals("Bearer " + API_KEY, server.authorization());
            assertEquals("embedding-model", server.requestBody().get("model").getAsString().value());
            assertEquals("hello world", server.requestBody().get("input").getAsArray().get(0).getAsString().value());
        }
    }

    @Test
    public void chatProvider_callsLoopbackAndParsesAnswer() throws Exception {
        try (TestServer server = TestServer.responding(200,
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}")) {
            HttpChatCompletionProvider provider = new HttpChatCompletionProvider(configuration(Duration.ofSeconds(2), 10),
                    server.uri(), "chat-model", API_KEY);

            assertEquals("answer", provider.complete("question", List.of("context")));
            assertEquals("chat-model", server.requestBody().get("model").getAsString().value());
            assertEquals(10, server.requestBody().get("max_tokens").getAsNumber().value().intValue());
            assertEquals(1, server.requestBody().get("messages").getAsArray().size());
            assertEquals("user", server.requestBody().get("messages").getAsArray().get(0).getAsObject()
                .get("role").getAsString().value());
            assertEquals("Question:\nquestion\n\nContext:\ncontext",
                server.requestBody().get("messages").getAsArray().get(0).getAsObject()
                    .get("content").getAsString().value());
        }
    }

    @Test
    public void chatProvider_acceptsAzureStyleChatCompletionsEndpoint() throws Exception {
        try (TestServer server = TestServer.responding(200,
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}")) {
            URI azureStyleEndpoint = URI.create(server.uri().toString() + "/chat/completions?api-version=2025-01-01-preview");
            HttpChatCompletionProvider provider = new HttpChatCompletionProvider(configuration(Duration.ofSeconds(2), 10),
                    azureStyleEndpoint, "chat-model", API_KEY);

            assertEquals("answer", provider.complete("question", List.of("context")));
            assertEquals("chat-model", server.requestBody().get("model").getAsString().value());
        }
    }

        @Test
        public void azureEndpoint_usesApiKeyHeaderInsteadOfBearerAuthorization() throws Exception {
            assertThrows(ProviderException.class,
                () -> new HttpEmbeddingProvider(configuration(Duration.ofSeconds(2), 10),
                    URI.create("https://test.openai.azure.com/provider"), "embedding-model", API_KEY)
                        .embed("hello world", 2));
        }

    @Test
    public void inputAboveQuota_isRejectedWithoutNetworkCall() {
        HttpEmbeddingProvider provider = new HttpEmbeddingProvider(configuration(Duration.ofSeconds(1), 2),
                URI.create("http://127.0.0.1:1"), "model", API_KEY);

        assertThrows(ProviderQuotaExceededException.class, () -> provider.embed("one two three", 2));
    }

    @Test
    public void remoteError_doesNotExposeSecretOrBody() throws Exception {
        String sensitiveBody = "{\"error\":\"" + API_KEY + " Bearer private request\"}";
        try (TestServer server = TestServer.responding(500, sensitiveBody)) {
            HttpEmbeddingProvider provider = new HttpEmbeddingProvider(configuration(Duration.ofSeconds(2), 10),
                    server.uri(), "model", API_KEY);

            ProviderException exception = assertThrows(ProviderException.class, () -> provider.embed("hello", 2));
            assertFalse(exception.toString().contains(API_KEY));
            assertFalse(exception.toString().contains("Bearer"));
            assertFalse(exception.toString().contains("private request"));
        }
    }

    @Test
    public void authenticationFailure_isClassifiedWithoutExposingSecret() throws Exception {
        try (TestServer server = TestServer.responding(401, "{\"error\":\"Unauthorized\"}")) {
            HttpEmbeddingProvider provider = new HttpEmbeddingProvider(configuration(Duration.ofSeconds(2), 10),
                    server.uri(), "model", API_KEY);

            ProviderException exception = assertThrows(ProviderException.class, () -> provider.embed("hello", 2));

            assertEquals(ProviderException.Category.AUTHENTICATION, exception.category());
            assertFalse(exception.toString().contains(API_KEY));
        }
    }

    @Test
    public void requestTimeout_isApplied() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestServer server = TestServer.blocking(release)) {
            HttpEmbeddingProvider provider = new HttpEmbeddingProvider(configuration(Duration.ofMillis(100), 10),
                    server.uri(), "model", API_KEY);

            try {
                ProviderException exception = assertThrows(ProviderException.class, () -> provider.embed("hello", 2));
                assertEquals(ProviderException.Category.TIMEOUT, exception.category());
            } finally {
                release.countDown();
            }
        }
    }

    private static ProviderConfiguration configuration(Duration timeout, int maximumTokens) {
        return new ProviderConfiguration(true, timeout, maximumTokens);
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private volatile String authorization;
        private volatile String apiKey;
        private volatile JsonObject requestBody;

        static TestServer responding(int status, String responseBody) throws IOException {
            return new TestServer(exchange -> write(exchange, status, responseBody));
        }

        static TestServer blocking(CountDownLatch release) throws IOException {
            return new TestServer(exchange -> {
                try {
                    release.await();
                    write(exchange, 200, "{\"data\":[{\"embedding\":[0,0]}]}");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    exchange.close();
                }
            });
        }

        private TestServer(ExchangeHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/provider", exchange -> {
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                apiKey = exchange.getRequestHeaders().getFirst("api-key");
                requestBody = JSON.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                handler.handle(exchange);
            });
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/provider");
        }

        String authorization() {
            return authorization;
        }

        String apiKey() {
            return apiKey;
        }

        JsonObject requestBody() {
            assertNotNull(requestBody);
            return requestBody;
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void write(HttpExchange exchange, int status, String responseBody) throws IOException {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @FunctionalInterface
        private interface ExchangeHandler {
            void handle(HttpExchange exchange) throws IOException;
        }
    }
}