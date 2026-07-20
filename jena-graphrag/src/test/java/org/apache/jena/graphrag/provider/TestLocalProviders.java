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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

public class TestLocalProviders {

    @Test
    public void mockEmbeddingProvider_isDeterministic() {
        MockEmbeddingProvider provider = new MockEmbeddingProvider();

        assertArrayEquals(provider.embed("bonjour", 8), provider.embed("bonjour", 8));
        assertEquals(8, provider.embed("bonjour", 8).length);
    }

    @Test
    public void mockChatCompletionProvider_isDeterministic() {
        MockChatCompletionProvider provider = new MockChatCompletionProvider();
        List<String> context = List.of("first", "second");

        assertEquals(provider.complete("question", context), provider.complete("question", context));
        assertEquals("[mock] Answer for 'question' based on 2 passage(s).", provider.complete("question", context));
    }

    @Test
    public void extractionMocks_returnStableEmptyResults() {
        assertEquals(List.of(), new MockEntityExtractor().extract("Alice knows Bob."));
        assertEquals(List.of(), new MockRelationshipExtractor()
                .extract("Alice knows Bob.", List.of("Alice", "Bob")));
    }

    @Test
    public void mockCommunitySummarizer_isDeterministic() {
        MockCommunitySummarizer summarizer = new MockCommunitySummarizer();

        assertEquals("[mock] Summary for 'community-1' based on 2 finding(s).",
                summarizer.summarize("community-1", List.of("one", "two")));
    }

    @Test
    public void localDefaults_disableExternalCalls() {
        ProviderConfiguration configuration = ProviderConfiguration.localDefaults();

        assertFalse(configuration.allowExternalCalls());
        assertEquals(Duration.ofSeconds(30), configuration.timeout());
        assertEquals(4096, configuration.maxTokensPerRequest());
    }

    @Test
    public void providerConfiguration_rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderConfiguration(false, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderConfiguration(false, Duration.ofSeconds(1), 0));
    }
}