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

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.graphrag.index.EmbeddingProvider;

/** OpenAI-compatible embedding provider, disabled unless external calls are explicitly allowed. */
public final class HttpEmbeddingProvider implements EmbeddingProvider {
    private final OpenAICompatibleClient client;

    public HttpEmbeddingProvider(ProviderConfiguration configuration, URI endpoint, String model, String apiKey) {
        this.client = new OpenAICompatibleClient(configuration, endpoint, model, apiKey);
    }

    @Override
    public float[] embed(String text, int dimension) {
        client.checkInputQuota(text);
        JsonBuilder builder = new JsonBuilder();
        JsonValue request = builder.startObject()
                .pair("model", client.model())
                .pair("input", text)
            .pair("dimensions", dimension)
                .finishObject()
                .build();
        JsonObject response = client.post(request);
        try {
            JsonArray values = response.get("data").getAsArray().get(0).getAsObject()
                    .get("embedding").getAsArray();
            if ( values.size() != dimension )
                throw new ProviderException("Provider returned an unexpected embedding dimension");
            float[] vector = new float[dimension];
            for ( int index = 0; index < dimension; index++ )
                vector[index] = values.get(index).getAsNumber().value().floatValue();
            return vector;
        } catch (NullPointerException | ClassCastException ex) {
            throw new ProviderException("Provider returned an invalid embedding response");
        }
    }
}