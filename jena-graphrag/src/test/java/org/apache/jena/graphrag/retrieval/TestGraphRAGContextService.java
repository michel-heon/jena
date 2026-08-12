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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.apache.jena.graphrag.GraphRAGImporter;
import org.apache.jena.graphrag.index.GraphRAGTextDatasetFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGContextService {

    private static final String SOURCE = "/org/apache/jena/graphrag/graphrag-sample-source.ttl";
    private static final String REFERENCE_CORPUS = "/org/apache/jena/graphrag/graphrag-retrieval-reference.ttl";

    private static Dataset dataset() {
        Model source = ModelFactory.createDefaultModel();
        try (InputStream in = TestGraphRAGContextService.class.getResourceAsStream(SOURCE)) {
            source.read(in, null, "TURTLE");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Dataset dataset = DatasetFactory.createTxnMem();
        GraphRAGImporter.load(source, dataset);
        return dataset;
    }

    @Test
    public void retrieve_returnsCitedLocalContext() {
        Dataset dataset = dataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "scrooge", 5);

            assertEquals("local", context.mode());
            assertEquals(3, context.results().size());
            GraphRAGContext.Result result = context.results().getFirst();
            assertEquals("MARLEY", result.neighborName());
            assertTrue(result.sourceText().contains("partner"));
            assertTrue(result.uri().contains("related_to_0001"));
            assertEquals(223.0, result.weight());
            assertEquals("chunk", context.results().get(2).type());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveLocal_matchesEntityNameContainedInQuestion() {
        Dataset dataset = dataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "What is Scrooge?", 5);

            assertEquals("local", context.mode());
            assertEquals(3, context.results().size());
            assertEquals("MARLEY", context.results().getFirst().neighborName());
            assertEquals("chunk", context.results().get(2).type());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieve_emptyDatasetReturnsNoResults() {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "test", 5);
            assertTrue(context.results().isEmpty());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveBasic_returnsChunkContextFromTextIndex() {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            Resource chunk = dataset.getDefaultModel().createResource("urn:chunk:basic");
            chunk.addProperty(RDF.type, GRAG.Chunk)
                 .addProperty(GRAG.text, "Scrooge signed the ledger in the counting-house.");
            dataset.commit();
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "basic", "ledger", 5);

            assertEquals("basic", context.mode());
            assertEquals(1, context.results().size());
            GraphRAGContext.Result result = context.results().getFirst();
            assertEquals("chunk", result.type());
            assertEquals("urn:chunk:basic", result.chunkUri());
            assertTrue(result.sourceText().contains("ledger"));
            assertTrue(result.score() > 0.0);
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveLocal_usesTextMatchedChunksAsEntitySeeds() {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            Resource scrooge = addEntity(dataset, "urn:entity:scrooge", "SCROOGE");
            Resource marley = addEntity(dataset, "urn:entity:marley", "MARLEY");
            Resource chunk = addChunk(dataset, "urn:chunk:ledger",
                    "Scrooge signed the ledger in the counting-house.");
            scrooge.addProperty(GRAG.hasEntity, chunk);
            addRelationship(dataset, "urn:rel:partner", scrooge, marley,
                    "Scrooge was the business partner of Marley.", 223.0, 6);
            dataset.commit();
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "local", "ledger", 5);

            assertEquals("local", context.mode());
                assertEquals(3, context.results().size());
            GraphRAGContext.Result result = context.results().getFirst();
            assertEquals("relationship", result.type());
            assertEquals("SCROOGE", result.entityName());
            assertEquals("MARLEY", result.neighborName());
            assertEquals("urn:chunk:ledger", result.chunkUri());
            assertTrue(result.chunkText().contains("ledger"));
            assertTrue(result.sourceText().contains("partner"));
            assertTrue(result.score() > 0.0);
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveLocal_ordersTextSeededRelationshipsByWeightThenUri() {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            Resource scrooge = addEntity(dataset, "urn:entity:scrooge", "SCROOGE");
            Resource marley = addEntity(dataset, "urn:entity:marley", "MARLEY");
            Resource belle = addEntity(dataset, "urn:entity:belle", "BELLE");
            Resource chunk = addChunk(dataset, "urn:chunk:ledger",
                    "Scrooge signed the ledger in the counting-house.");
            chunk.addProperty(GRAG.hasEntity, scrooge);
            addRelationship(dataset, "urn:rel:marley", scrooge, marley,
                    "Scrooge was the business partner of Marley.", 2.0, 6);
            addRelationship(dataset, "urn:rel:belle", scrooge, belle,
                    "Scrooge's choices affected Belle.", 8.0, 1);
            dataset.commit();
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "local", "ledger", 2);

            assertEquals(2, context.results().size());
            assertEquals("BELLE", context.results().get(0).neighborName());
            assertEquals("MARLEY", context.results().get(1).neighborName());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveGlobal_returnsCommunityContext() {
        Dataset dataset = globalDataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "global", "resilience", 3);

            assertEquals("global", context.mode());
            assertEquals(1, context.results().size());
            GraphRAGContext.Result result = context.results().getFirst();
            assertEquals("community", result.type());
            assertEquals("urn:community:climate", result.uri());
            assertEquals("Climate transition", result.communityTitle());
            assertTrue(result.sourceText().contains("resilience"));
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveGlobal_emptyDatasetReturnsNoResults() {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "global", "resilience", 3);
            assertTrue(context.results().isEmpty());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveGlobal_referenceCorpusDiversifiesCommunityLevelsBeforeFillingBudget() {
        Dataset dataset = referenceDataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "global", "GraphRAG", 3);

            assertEquals(3, context.results().size());
            assertEquals("urn:graphrag:reference:community-governance", context.results().get(0).uri());
            assertEquals("urn:graphrag:reference:community-platform", context.results().get(1).uri());
            assertEquals("urn:graphrag:reference:community-retrieval", context.results().get(2).uri());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void referenceCorpus_contractsRetrievalModesAndCitations() {
        Dataset dataset = referenceDataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContextService service = new GraphRAGContextService();

            GraphRAGContext basic = service.retrieve(dataset.asDatasetGraph(), "basic", "semantic retrieval", 1);
            assertEquals(1, basic.results().size());
            assertEquals("chunk", basic.results().getFirst().type());
            assertEquals("urn:graphrag:reference:chunk-retrieval", basic.results().getFirst().uri());
            assertEquals(basic.results().getFirst().uri(), basic.results().getFirst().chunkUri());

            GraphRAGContext local = service.retrieve(dataset.asDatasetGraph(), "local", "GraphRAG", 1);
            assertEquals(1, local.results().size());
            assertEquals("relationship", local.results().getFirst().type());
            assertEquals("urn:graphrag:reference:relationship-graphrag-jena", local.results().getFirst().uri());
            assertEquals("GraphRAG", local.results().getFirst().entityName());
            assertEquals("Jena", local.results().getFirst().neighborName());

            GraphRAGContext global = service.retrieve(dataset.asDatasetGraph(), "global", "platform overview", 1);
            assertEquals(1, global.results().size());
            assertEquals("community", global.results().getFirst().type());
            assertEquals("urn:graphrag:reference:community-platform", global.results().getFirst().uri());
            assertEquals("Platform overview", global.results().getFirst().communityTitle());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void basicAndGlobal_retrieveFromNaturalLanguageQuestion() {
        Dataset dataset = referenceDataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContextService service = new GraphRAGContextService();

            GraphRAGContext basic = service.retrieve(dataset.asDatasetGraph(), "basic", "What is GraphRAG?", 1);
            assertEquals(1, basic.results().size());
            assertEquals("chunk", basic.results().getFirst().type());

            GraphRAGContext global = service.retrieve(dataset.asDatasetGraph(), "global", "What is GraphRAG?", 1);
            assertEquals(1, global.results().size());
            assertEquals("community", global.results().getFirst().type());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveLocal_referenceCorpusReturnsMixedRelevantContext() {
        Dataset dataset = referenceDataset();
        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "local", "GraphRAG", 4);

            assertEquals(4, context.results().size());
            assertEquals("relationship", context.results().get(0).type());
            assertEquals("urn:graphrag:reference:relationship-graphrag-jena", context.results().get(0).uri());
            assertEquals("entity", context.results().get(1).type());
            assertEquals("urn:graphrag:reference:entity-graphrag", context.results().get(1).uri());
            assertEquals("chunk", context.results().get(2).type());
            assertEquals("urn:graphrag:reference:chunk-retrieval", context.results().get(2).uri());
            assertEquals("community", context.results().get(3).type());
            assertEquals("urn:graphrag:reference:community-retrieval", context.results().get(3).uri());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieveLocal_fallbackIncludesLinkedChunkAndCommunity() {
        Dataset dataset = DatasetFactory.createTxnMem();
        dataset.begin(ReadWrite.WRITE);
        try {
            Resource graphrag = addEntity(dataset, "urn:entity:graphrag", "GraphRAG");
            Resource jena = addEntity(dataset, "urn:entity:jena", "Jena");
            Resource chunk = addChunk(dataset, "urn:chunk:graphrag", "GraphRAG provides retrieval context.");
            chunk.addProperty(GRAG.hasEntity, graphrag);
            addRelationship(dataset, "urn:rel:graphrag-jena", graphrag, jena,
                    "GraphRAG uses Jena.", 1.0, 1);
            addCommunity(dataset, "urn:community:graphrag", "GraphRAG", "GraphRAG context", "GraphRAG report");
            graphrag.addProperty(GRAG.inCommunity, dataset.getDefaultModel().getResource("urn:community:graphrag"));
            dataset.commit();
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try {
            GraphRAGContext context = new GraphRAGContextService()
                    .retrieve(dataset.asDatasetGraph(), "local", "What is GraphRAG?", 4);

            assertEquals(4, context.results().size());
            assertEquals("relationship", context.results().get(0).type());
            assertEquals("entity", context.results().get(1).type());
            assertEquals("chunk", context.results().get(2).type());
            assertEquals("urn:chunk:graphrag", context.results().get(2).uri());
            assertEquals("community", context.results().get(3).type());
        } finally {
            dataset.end();
        }
    }

    @Test
    public void retrieve_validatesParameters() {
        Dataset dataset = DatasetFactory.createTxnMem();
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), " ", 5));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), "test", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), "test", 101));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphRAGContextService().retrieve(dataset.asDatasetGraph(), "drift", "test", 5));
    }

    private static Dataset globalDataset() {
        Dataset base = DatasetFactory.createTxnMem();
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(base, new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try {
            addCommunity(dataset, "urn:community:climate", "Climate transition",
                    "Climate resilience planning", "Detailed resilience investments");
            addCommunity(dataset, "urn:community:finance", "Finance controls",
                    "Budget governance", "Financial oversight");
            dataset.commit();
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static Dataset referenceDataset() {
        Dataset dataset = GraphRAGTextDatasetFactory.createRetrievalTextDataset(
                DatasetFactory.createTxnMem(), new ByteBuffersDirectory());
        dataset.begin(ReadWrite.WRITE);
        try (InputStream in = TestGraphRAGContextService.class.getResourceAsStream(REFERENCE_CORPUS)) {
            dataset.getDefaultModel().read(in, null, "TURTLE");
            dataset.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            dataset.end();
        }
        return dataset;
    }

    private static void addCommunity(Dataset dataset, String uri, String title, String summary, String fullContent) {
        Resource community = dataset.getDefaultModel().createResource(uri);
        community.addProperty(RDF.type, GRAG.Community)
                 .addProperty(GRAG.title, title)
                 .addProperty(GRAG.summary, summary)
                 .addProperty(GRAG.fullContent, fullContent);
    }

    private static Resource addEntity(Dataset dataset, String uri, String name) {
        Resource entity = dataset.getDefaultModel().createResource(uri);
        entity.addProperty(RDF.type, GRAG.Entity)
              .addProperty(GRAG.name, name);
        return entity;
    }

    private static Resource addChunk(Dataset dataset, String uri, String text) {
        Resource chunk = dataset.getDefaultModel().createResource(uri);
        chunk.addProperty(RDF.type, GRAG.Chunk)
             .addProperty(GRAG.text, text);
        return chunk;
    }

    private static Resource addRelationship(Dataset dataset, String uri, Resource source, Resource target,
            String description, double weight, int rank) {
        Resource relationship = dataset.getDefaultModel().createResource(uri);
        relationship.addProperty(RDF.type, GRAG.Relationship)
                    .addProperty(GRAG.source, source)
                    .addProperty(GRAG.target, target)
                    .addProperty(GRAG.description, description)
                    .addLiteral(GRAG.weight, weight)
                    .addLiteral(GRAG.rank, rank);
        return relationship;
    }
}