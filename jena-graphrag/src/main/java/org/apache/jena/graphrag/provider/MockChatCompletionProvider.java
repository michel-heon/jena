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
 * Deterministic mock implementation of {@link ChatCompletionProvider}.
 *
 * <p>Returns a fixed response derived from the question and context size.
 * Uses no network and requires no API key. Always safe to instantiate.
 */
public final class MockChatCompletionProvider implements ChatCompletionProvider {

    @Override
    public String complete(String question, List<String> contextPassages) {
        if ( contextPassages.isEmpty() )
            return "[mock] No context available for: " + question;
        return "[mock] Answer for '" + question + "' based on " + contextPassages.size() + " passage(s).";
    }
}
