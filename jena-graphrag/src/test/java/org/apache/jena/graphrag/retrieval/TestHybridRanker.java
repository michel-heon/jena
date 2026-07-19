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

package org.apache.jena.graphrag.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.apache.jena.graphrag.retrieval.HybridRanker.ScoredResult;
import org.junit.jupiter.api.Test;

public class TestHybridRanker {

    private static final List<ScoredResult> TEXT_RESULTS = List.of(
            new ScoredResult("urn:chunk:text", 1.0),
            new ScoredResult("urn:chunk:both", 0.6));

    private static final List<ScoredResult> VECTOR_RESULTS = List.of(
            new ScoredResult("urn:chunk:vector", 1.0),
            new ScoredResult("urn:chunk:both", 0.8));

    @Test
    public void rank_alphaOneUsesTextScores() {
        List<String> uris = new HybridRanker(1.0).rank(TEXT_RESULTS, VECTOR_RESULTS, 3).stream()
                .map(HybridRanker.HybridResult::uri)
                .toList();

        assertEquals(List.of("urn:chunk:text", "urn:chunk:both", "urn:chunk:vector"), uris);
    }

    @Test
    public void rank_alphaZeroUsesVectorScores() {
        List<String> uris = new HybridRanker(0.0).rank(TEXT_RESULTS, VECTOR_RESULTS, 3).stream()
                .map(HybridRanker.HybridResult::uri)
                .toList();

        assertEquals(List.of("urn:chunk:vector", "urn:chunk:both", "urn:chunk:text"), uris);
    }

    @Test
    public void rank_halfAlphaCombinesTextAndVectorScores() {
        var results = new HybridRanker(0.5).rank(TEXT_RESULTS, VECTOR_RESULTS, 2);

        assertEquals("urn:chunk:both", results.getFirst().uri());
        assertEquals(0.7, results.getFirst().score(), 0.0001);
        assertEquals(2, results.size());
    }

    @Test
    public void constructor_rejectsInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new HybridRanker(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new HybridRanker(1.1));
    }
}