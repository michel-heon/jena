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

package org.apache.jena.vocabulary;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

public class TestGragTurtle {

    @Test
    public void turtleResource_loadsAndContainsMinimalClasses() {
        InputStream in = TestGragTurtle.class.getResourceAsStream("/org/apache/jena/graphrag/grag.ttl");
        assertNotNull(in, "grag.ttl resource not found");

        Model m = ModelFactory.createDefaultModel();
        m.read(in, GRAG.uri, "TRIG");

        String[] locals = {
            "Document", "Chunk", "Entity",
            "Community", "Covariate", "Finding", "Relationship"
        };
        for (String local : locals) {
            assertTrue(m.containsResource(m.createResource(GRAG.uri + local)),
                       "Missing class in grag.ttl: " + local);
        }
    }

    @Test
    public void turtleResource_containsProperties() {
        java.io.InputStream in = TestGragTurtle.class.getResourceAsStream("/org/apache/jena/graphrag/grag.ttl");
        assertNotNull(in, "grag.ttl resource not found");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, GRAG.uri, "TRIG");

        String[] propLocals = {
            "hasCovariate", "hasFinding", "inCommunity", "relatedTo", "hasEntity", "partOf",
            "source", "target",
            "documentIds", "textUnitId", "finding", "summary",
            "rankExplanation", "level", "fullContent", "rank",
            "descriptionEmbedding", "humanReadableId", "description", "name",
            "text", "nTokens", "id", "title",
            "weight", "type"
        };
        for (String local : propLocals) {
            assertTrue(m.containsResource(m.createResource(GRAG.uri + local)),
                       "Missing property in grag.ttl: " + local);
        }
    }
}
