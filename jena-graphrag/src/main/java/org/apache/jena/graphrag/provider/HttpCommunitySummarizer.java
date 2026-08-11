/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Summarizes community findings through an OpenAI-compatible chat completion endpoint. */
public final class HttpCommunitySummarizer implements CommunitySummarizer {
    private static final String SYSTEM_PROMPT = "Summarize the supplied GraphRAG community findings. "
            + "Return only JSON with this schema: {\"summary\":\"summary text\"}.";

    private final ChatCompletionProvider chatProvider;

    public HttpCommunitySummarizer(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this(new HttpChatCompletionProvider(configuration, endpoint, model, apiKey));
    }

    HttpCommunitySummarizer(ChatCompletionProvider chatProvider) {
        this.chatProvider = Objects.requireNonNull(chatProvider, "chatProvider");
    }

    @Override
    public String summarize(String communityName, List<String> findings) {
        String name = Objects.requireNonNull(communityName, "communityName");
        List<String> suppliedFindings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if ( suppliedFindings.isEmpty() )
            return "No extracted relationships for " + name + ".";
        String response = chatProvider.complete("Summarize community: " + name + ".", suppliedFindings, SYSTEM_PROMPT);
        return ExtractionJson.string(ExtractionJson.object(response), "summary");
    }
}