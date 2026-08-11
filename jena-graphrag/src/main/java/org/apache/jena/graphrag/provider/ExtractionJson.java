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

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

final class ExtractionJson {
    private ExtractionJson() {}

    static JsonObject object(String response) {
        try {
            return JSON.parse(unfence(response));
        } catch (RuntimeException ex) {
            throw invalid(ex);
        }
    }

    static List<String> strings(JsonObject object, String field) {
        try {
            List<String> values = new ArrayList<>();
            for (JsonValue value : required(object, field).getAsArray())
                values.add(nonBlank(value.getAsString().value()));
            return List.copyOf(values);
        } catch (RuntimeException ex) {
            throw invalid(ex);
        }
    }

    static String string(JsonObject object, String field) {
        try {
            return nonBlank(required(object, field).getAsString().value());
        } catch (RuntimeException ex) {
            throw invalid(ex);
        }
    }

    static JsonValue required(JsonObject object, String field) {
        JsonValue value = object.get(field);
        if (value == null)
            throw new IllegalArgumentException("missing required field");
        return value;
    }

    static ProviderException invalid(Throwable cause) {
        return new ProviderException("Provider returned invalid extraction JSON", cause);
    }

    private static String unfence(String response) {
        String value = response.strip();
        if ( !value.startsWith("```") )
            return value;
        int contentStart = value.indexOf('\n');
        if ( contentStart < 0 || !value.endsWith("```") )
            throw new IllegalArgumentException("expected a complete JSON code fence");
        return value.substring(contentStart + 1, value.length() - 3).strip();
    }

    private static String nonBlank(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("expected a non-blank string");
        return value;
    }
}