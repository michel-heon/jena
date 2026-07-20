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

import org.apache.jena.assembler.Assembler;
import org.apache.jena.query.text.TextQuery;
import org.apache.jena.sys.JenaSystem;

/** Registers GraphRAG RDF assembler types. */
public final class GraphRAGAssembler {
    private static volatile boolean initialized = false;
    private static final Object lock = new Object();

    private GraphRAGAssembler() {}

    /** Registers {@code grag:GraphRAGIndex} with the global Jena assembler group. */
    public static void init() {
        if ( initialized )
            return;
        synchronized (lock) {
            if ( initialized ) {
                JenaSystem.logLifecycle("GraphRAGAssembler.init - skip");
                return;
            }
            initialized = true;
            JenaSystem.logLifecycle("GraphRAGAssembler.init - start");
            TextQuery.init();
            Assembler.general().implementWith(GraphRAGAssemblerVocab.GraphRAGIndex, new GraphRAGIndexAssembler());
            JenaSystem.logLifecycle("GraphRAGAssembler.init - finish");
        }
    }
}