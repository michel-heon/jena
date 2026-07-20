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

import java.time.Duration;
import java.util.Objects;

/** Minimal runtime limits shared by optional GraphRAG providers. */
public record ProviderConfiguration(boolean allowExternalCalls, Duration timeout, int maxTokensPerRequest) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    public static final int DEFAULT_MAX_TOKENS_PER_REQUEST = 4096;

    public ProviderConfiguration {
        Objects.requireNonNull(timeout, "timeout");
        if ( timeout.isZero() || timeout.isNegative() )
            throw new IllegalArgumentException("timeout must be positive");
        if ( maxTokensPerRequest < 1 )
            throw new IllegalArgumentException("maxTokensPerRequest must be positive");
    }

    /** Returns the hermetic default configuration: no external calls and no API key required. */
    public static ProviderConfiguration localDefaults() {
        return new ProviderConfiguration(false, DEFAULT_TIMEOUT, DEFAULT_MAX_TOKENS_PER_REQUEST);
    }
}