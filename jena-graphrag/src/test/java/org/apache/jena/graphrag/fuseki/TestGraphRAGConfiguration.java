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

package org.apache.jena.graphrag.fuseki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGConfiguration {

    @Test
    public void systemPropertiesProvideDefaults() {
        GraphRAGConfiguration configuration = GraphRAGConfiguration.fromSystemProperties();

        assertEquals("local", configuration.defaultMode());
        assertTrue(configuration.defaultTopK() > 0);
        assertTrue(configuration.maxTopK() >= configuration.defaultTopK());
        assertEquals(0.5, configuration.hybridAlpha());
    }

    @Test
    public void acceptsGlobalDefaultMode() {
        GraphRAGConfiguration configuration = new GraphRAGConfiguration("global", 5, 100, 0.5);

        assertEquals("global", configuration.defaultMode());
    }

    @Test
    public void rejectsUnsupportedDefaultMode() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("drift", 5, 100, 0.5));
    }

    @Test
    public void rejectsInvalidTopKBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 0, 100, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 10, 5, 0.5));
        }

        @Test
        public void rejectsInvalidHybridAlpha() {
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, -0.1));
        assertThrows(IllegalArgumentException.class,
            () -> new GraphRAGConfiguration("local", 5, 100, 1.1));
    }

        @Test
        public void modelOverridesHybridAlpha() {
            Model config = ModelFactory.createDefaultModel();
            config.createResource("urn:graphrag:index")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "hybridAlpha"), 0.25);

            GraphRAGConfiguration configuration = GraphRAGConfiguration.fromModel(config);

            assertEquals(0.25, configuration.hybridAlpha());
        }
}