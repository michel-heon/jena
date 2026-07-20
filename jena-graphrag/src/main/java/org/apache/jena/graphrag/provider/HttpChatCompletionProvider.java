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
import java.util.List;

import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

/** OpenAI-compatible chat provider, disabled unless external calls are explicitly allowed. */
public final class HttpChatCompletionProvider implements ChatCompletionProvider {
    private final OpenAICompatibleClient client;

    public HttpChatCompletionProvider(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.client = new OpenAICompatibleClient(configuration, endpoint, model, apiKey);
    }

    @Override
    public String complete(String question, List<String> contextPassages) {
        String prompt = "Question:\n" + question + "\n\nContext:\n" + String.join("\n\n", contextPassages);
        client.checkInputQuota(prompt);
        JsonBuilder builder = new JsonBuilder();
        JsonValue request = builder.startObject()
                .pair("model", client.model())
                .pair("max_tokens", client.maximumTokens())
                .key("messages").startArray()
                .startObject().pair("role", "user").pair("content", prompt).finishObject()
                .finishArray()
                .finishObject()
                .build();
        JsonObject response = client.post(request);
        try {
            return response.get("choices").getAsArray().get(0).getAsObject()
                    .get("message").getAsObject().get("content").getAsString().value();
        } catch (NullPointerException | ClassCastException ex) {
            throw new ProviderException("Provider returned an invalid chat response");
        }
    }
}