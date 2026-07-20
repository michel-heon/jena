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

import java.util.List;

/**
 * Generates a natural-language answer from a question and supporting context passages.
 *
 * <p>Implementations must be thread-safe. The default mock implementation
 * ({@link MockChatCompletionProvider}) requires no network access and is always
 * active unless an HTTP-capable provider is explicitly configured with
 * {@code grag:allowExternalCalls true}.
 */
@FunctionalInterface
public interface ChatCompletionProvider {

    /**
     * Generates a completion given a question and supporting context passages.
     *
     * @param question        the user question; never {@code null} or blank
     * @param contextPassages relevant text passages retrieved by GraphRAG search;
     *                        may be empty when no context is available
     * @return the generated answer text; never {@code null}
     */
    String complete(String question, List<String> contextPassages);
}
