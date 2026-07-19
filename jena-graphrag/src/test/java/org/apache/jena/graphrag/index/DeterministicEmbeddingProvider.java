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

package org.apache.jena.graphrag.index;

final class DeterministicEmbeddingProvider implements EmbeddingProvider {

    private int calls;

    @Override
    public float[] embed(String text, int dimension) {
        calls++;
        return vectorFor(text, dimension);
    }

    int calls() {
        return calls;
    }

    static float[] vectorFor(String text, int dimension) {
        float[] vector = new float[dimension];
        int hash = text.hashCode();
        for ( int i = 0; i < dimension; i++ )
            vector[i] = ((hash >>> ((i % 4) * 8)) & 0xff) / 255.0f;
        return vector;
    }
}