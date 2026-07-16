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

package org.apache.jena.graphrag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.GRAG;
import org.junit.jupiter.api.Test;

public class TestGraphRAGImporter {

    private static final String SOURCE = "/org/apache/jena/graphrag/graphrag-sample-source.ttl";
    private static final String CONTEXT_QUERY = "/org/apache/jena/graphrag/queries/context-local-entity.rq";

    private static Model loadSource() {
        InputStream in = TestGraphRAGImporter.class.getResourceAsStream(SOURCE);
        assertNotNull(in, "sample source fixture not found");
        Model m = ModelFactory.createDefaultModel();
        m.read(in, null, "TURTLE");
        return m;
    }

    private static String readResource(String path) {
        try (InputStream in = TestGraphRAGImporter.class.getResourceAsStream(path)) {
            assertNotNull(in, "resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long countType(Dataset ds, org.apache.jena.rdf.model.Resource type) {
        Query q = QueryFactory.create(
                "SELECT (COUNT(DISTINCT ?s) AS ?c) WHERE { ?s a <" + type.getURI() + "> }");
        ds.begin(ReadWrite.READ);
        try (QueryExecution qexec = QueryExecution.dataset(ds).query(q).build()) {
            ResultSet rs = qexec.execSelect();
            return rs.next().getLiteral("c").getLong();
        } finally {
            ds.end();
        }
    }

    @Test
    public void normalize_producesExpectedCounts() {
        Dataset ds = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(loadSource(), ds);

        assertEquals(1, countType(ds, GRAG.Document), "Document count");
        assertEquals(2, countType(ds, GRAG.Chunk), "Chunk count");
        assertEquals(2, countType(ds, GRAG.Entity), "Entity count");
        assertEquals(1, countType(ds, GRAG.Relationship), "Relationship count");
        assertEquals(1, countType(ds, GRAG.Community), "Community count");
        assertEquals(1, countType(ds, GRAG.Finding), "Finding count");
    }

    @Test
    public void normalize_rewritesSnakeCaseToCamelCase() {
        Model out = GraphRAGImporter.normalize(loadSource());

        // camelCase predicates present
        assertTrue(out.contains(null, GRAG.hasEntity, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:hasEntity expected");
        assertTrue(out.contains(null, GRAG.partOf, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:partOf expected");
        assertTrue(out.contains(null, GRAG.inCommunity, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:inCommunity expected");
        assertTrue(out.contains(null, GRAG.hasFinding, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:hasFinding expected");
        assertTrue(out.contains(null, GRAG.nTokens, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:nTokens expected");

        // snake_case predicates absent
        assertFalse(out.contains(null, out.createProperty(GRAG.uri + "has_entity"),
                    (org.apache.jena.rdf.model.RDFNode) null), "snake_case has_entity should be gone");
        assertFalse(out.contains(null, out.createProperty(GRAG.uri + "n_tokens"),
                    (org.apache.jena.rdf.model.RDFNode) null), "snake_case n_tokens should be gone");
    }

    @Test
    public void normalize_reifiesRelationshipWithSourceTargetAndDirectEdge() {
        Model out = GraphRAGImporter.normalize(loadSource());

        assertEquals(1, out.listSubjectsWithProperty(
                org.apache.jena.vocabulary.RDF.type, GRAG.Relationship).toList().size(),
                "exactly one mg:Relationship node");
        assertTrue(out.contains(null, GRAG.source, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:source expected");
        assertTrue(out.contains(null, GRAG.target, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:target expected");
        // convenience direct edge
        assertTrue(out.contains(null, GRAG.relatedTo, (org.apache.jena.rdf.model.RDFNode) null),
                   "direct mg:relatedTo edge expected");
        // relationship attributes preserved
        assertTrue(out.contains(null, GRAG.weight, (org.apache.jena.rdf.model.RDFNode) null),
                   "mg:weight expected on relationship");
    }

    @Test
    public void load_isIdempotent() {
        Dataset ds = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(loadSource(), ds);
        long first = countType(ds, GRAG.Entity);
        GraphRAGImporter.load(loadSource(), ds);
        long second = countType(ds, GRAG.Entity);
        assertEquals(first, second, "loading twice must not duplicate entities");
        assertEquals(1, countType(ds, GRAG.Relationship), "relationship not duplicated");
    }

    @Test
    public void contextQuery_returnsRelationshipEvidence() {
        Dataset ds = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(loadSource(), ds);

        Query query = QueryFactory.create(readResource(CONTEXT_QUERY));
        QuerySolutionMap initial = new QuerySolutionMap();
        initial.add("entityName", ModelFactory.createDefaultModel().createLiteral("SCROOGE"));

        ds.begin(ReadWrite.READ);
        try (QueryExecution qexec = QueryExecution.dataset(ds).query(query)
                .substitution(initial).build()) {
            ResultSet rs = qexec.execSelect();
            assertTrue(rs.hasNext(), "context query must return at least one neighbour");
            QuerySolution sol = rs.nextSolution();
            assertEquals("MARLEY", sol.getLiteral("neighborName").getString(), "neighbour name");
            assertNotNull(sol.getLiteral("relDescription"), "relationship description expected");
            assertTrue(sol.getLiteral("relDescription").getString().contains("partner"),
                       "relationship evidence carried through");
        } finally {
            ds.end();
        }
    }
}
