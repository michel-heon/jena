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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies configurable linear fusion to text and vector retrieval scores. */
public final class HybridRanker {

    private final double hybridAlpha;

    public HybridRanker(double hybridAlpha) {
        if ( hybridAlpha < 0.0 || hybridAlpha > 1.0 )
            throw new IllegalArgumentException("hybridAlpha must be between 0.0 and 1.0: " + hybridAlpha);
        this.hybridAlpha = hybridAlpha;
    }

    public List<HybridResult> rank(List<ScoredResult> textResults, List<ScoredResult> vectorResults, int topK) {
        Objects.requireNonNull(textResults, "textResults");
        Objects.requireNonNull(vectorResults, "vectorResults");
        if ( topK < 1 )
            throw new IllegalArgumentException("topK must be greater than zero");

        Map<String, MutableScore> scores = new LinkedHashMap<>();
        textResults.forEach(result -> scores.computeIfAbsent(result.uri(), MutableScore::new).textScore = result.score());
        vectorResults.forEach(result -> scores.computeIfAbsent(result.uri(), MutableScore::new).vectorScore = result.score());

        return scores.values().stream()
                .map(score -> new HybridResult(score.uri, score.textScore, score.vectorScore,
                        hybridAlpha * score.textScore + (1.0 - hybridAlpha) * score.vectorScore))
                .sorted(Comparator.comparingDouble(HybridResult::score).reversed()
                        .thenComparing(HybridResult::uri))
                .limit(topK)
                .toList();
    }

    public double hybridAlpha() {
        return hybridAlpha;
    }

    public record ScoredResult(String uri, double score) {
        public ScoredResult {
            if ( uri == null || uri.isBlank() )
                throw new IllegalArgumentException("uri must not be blank");
        }
    }

    public record HybridResult(String uri, double textScore, double vectorScore, double score) {}

    private static final class MutableScore {
        private final String uri;
        private double textScore;
        private double vectorScore;

        private MutableScore(String uri) {
            this.uri = uri;
        }
    }
}