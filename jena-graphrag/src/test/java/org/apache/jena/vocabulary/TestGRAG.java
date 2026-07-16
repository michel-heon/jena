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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

public class TestGRAG {

    @Test
    public void namespace_isStable() {
        assertEquals("http://ormynet.com/ns/msft-graphrag#", GRAG.uri);
        assertEquals(GRAG.uri, GRAG.getURI());
        assertNotNull(GRAG.NAMESPACE);
        assertEquals(GRAG.uri, GRAG.NAMESPACE.getURI());
    }

    @Test
    public void minimalClasses_areDeclared() {
        Resource[] classes = {
            GRAG.Document,
            GRAG.Chunk,
            GRAG.Entity,
            GRAG.Community,
            GRAG.Covariate,
            GRAG.Finding,
            GRAG.Relationship
        };
        String[] locals = {
            "Document", "Chunk", "Entity",
            "Community", "Covariate", "Finding", "Relationship"
        };
        for (int i = 0; i < classes.length; i++) {
            assertNotNull(classes[i], "class is null: " + locals[i]);
            assertEquals(GRAG.uri + locals[i], classes[i].getURI());
        }
    }

    @Test
    public void properties_areDeclared() {
        Property[] props = {
            GRAG.hasCovariate, GRAG.hasFinding, GRAG.inCommunity, GRAG.relatedTo, GRAG.hasEntity, GRAG.partOf,
            GRAG.source, GRAG.target,
            GRAG.documentIds, GRAG.textUnitId, GRAG.finding, GRAG.summary,
            GRAG.rankExplanation, GRAG.level, GRAG.fullContent, GRAG.rank,
            GRAG.descriptionEmbedding, GRAG.humanReadableId, GRAG.description, GRAG.name,
            GRAG.text, GRAG.nTokens, GRAG.id, GRAG.title,
            GRAG.weight, GRAG.type
        };
        String[] locals = {
            "hasCovariate", "hasFinding", "inCommunity", "relatedTo", "hasEntity", "partOf",
            "source", "target",
            "documentIds", "textUnitId", "finding", "summary",
            "rankExplanation", "level", "fullContent", "rank",
            "descriptionEmbedding", "humanReadableId", "description", "name",
            "text", "nTokens", "id", "title",
            "weight", "type"
        };
        for (int i = 0; i < props.length; i++) {
            assertNotNull(props[i], "property is null: " + locals[i]);
            assertEquals(GRAG.uri + locals[i], props[i].getURI());
        }
    }
}
