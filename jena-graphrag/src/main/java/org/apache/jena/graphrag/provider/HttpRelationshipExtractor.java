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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

/** Extracts relationships between supplied entities through an OpenAI-compatible endpoint. */
public final class HttpRelationshipExtractor implements RelationshipExtractor {
    private static final String SYSTEM_PROMPT = "Extract relationships only between the supplied entities. "
            + "Return only JSON with this schema: {\"relationships\":[{\"source\":\"name\","
            + "\"target\":\"name\",\"description\":\"relation\"}]}.";

    private final ChatCompletionProvider chatProvider;

    public HttpRelationshipExtractor(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this(new HttpChatCompletionProvider(configuration, endpoint, model, apiKey));
    }

    HttpRelationshipExtractor(ChatCompletionProvider chatProvider) {
        this.chatProvider = Objects.requireNonNull(chatProvider, "chatProvider");
    }

    @Override
    public List<Relationship> extract(String text, List<String> entities) {
        Objects.requireNonNull(text, "text");
        List<String> suppliedEntities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        String question = "Extract relationships among these entities: " + String.join(", ", suppliedEntities) + ".";
        String response = chatProvider.complete(question, List.of(text), SYSTEM_PROMPT);
        return relationships(ExtractionJson.object(response));
    }

    private static List<Relationship> relationships(JsonObject object) {
        try {
            List<Relationship> relationships = new ArrayList<>();
            for (JsonValue value : ExtractionJson.required(object, "relationships").getAsArray()) {
                JsonObject relationship = value.getAsObject();
                relationships.add(new Relationship(ExtractionJson.string(relationship, "source"),
                        ExtractionJson.string(relationship, "target"), ExtractionJson.string(relationship, "description")));
            }
            return List.copyOf(relationships);
        } catch (ProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ExtractionJson.invalid(ex);
        }
    }
}