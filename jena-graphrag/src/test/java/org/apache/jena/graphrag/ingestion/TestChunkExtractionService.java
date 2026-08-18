/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.jena.graphrag.provider.CommunitySummarizer;
import org.apache.jena.graphrag.provider.EntityExtractor;
import org.apache.jena.graphrag.provider.RelationshipExtractor;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestChunkExtractionService {

    @TempDir
    Path temporaryDirectory;

    @Test
    public void enrich_createsEntitiesRelationshipsAndCommunitiesWithoutDuplicates() {
        Dataset dataset = DatasetFactory.createTxnMem();
        addChunk(dataset, "urn:chunk:one", "Alice collaborates with Bob.");
        addChunk(dataset, "urn:chunk:two", "Bob oversees Carol.");

        EntityExtractor entityExtractor = text -> text.startsWith("Alice")
                ? List.of("Alice", "Bob") : List.of("Bob", "Carol");
        RelationshipExtractor relationshipExtractor = (text, entities) -> text.startsWith("Alice")
                ? List.of(new RelationshipExtractor.Relationship("Alice", "Bob", "collaborates with"))
                : List.of(new RelationshipExtractor.Relationship("Bob", "Carol", "oversees"));
        CommunitySummarizer summarizer = (name, findings) -> "Summary for " + name + ": "
                + String.join(", ", findings);
        ChunkExtractionService service = new ChunkExtractionService("urn:graph:", entityExtractor,
                relationshipExtractor, summarizer);

        ChunkExtractionService.Result first = service.enrich(dataset);
        ChunkExtractionService.Result second = service.enrich(dataset);

        assertEquals(new ChunkExtractionService.Result(2, 3, 2, 1), first);
        assertEquals(first, second);
        dataset.begin(ReadWrite.READ);
        try {
            Model model = dataset.getDefaultModel();
            assertEquals(3, model.listResourcesWithProperty(RDF.type, GRAG.Entity).toList().size());
            assertEquals(2, model.listResourcesWithProperty(RDF.type, GRAG.Relationship).toList().size());
            assertEquals(1, model.listResourcesWithProperty(RDF.type, GRAG.Community).toList().size());
            assertEquals(1, model.listResourcesWithProperty(RDF.type, GRAG.Finding).toList().size());

            Resource alice = model.listResourcesWithProperty(GRAG.name, "Alice").next();
            Resource bob = model.listResourcesWithProperty(GRAG.name, "Bob").next();
            Resource carol = model.listResourcesWithProperty(GRAG.name, "Carol").next();
            assertTrue(model.contains(model.getResource("urn:chunk:one"), GRAG.hasEntity, alice));
            assertTrue(model.contains(model.getResource("urn:chunk:two"), GRAG.hasEntity, carol));
            assertTrue(model.contains(alice, GRAG.relatedTo, bob));
            assertTrue(model.contains(bob, GRAG.relatedTo, carol));
            assertTrue(model.contains(alice, GRAG.inCommunity, (Resource)null));
            assertTrue(model.contains(carol, GRAG.inCommunity, (Resource)null));
        } finally {
            dataset.end();
        }
    }

    @Test
    public void enrich_reusesSuccessfulProviderResponsesAfterFailure() {
        Dataset dataset = DatasetFactory.createTxnMem();
        addChunk(dataset, "urn:chunk:one", "Alice collaborates with Bob.");
        addChunk(dataset, "urn:chunk:two", "Bob oversees Carol.");
        Path checkpoint = temporaryDirectory.resolve("extraction.properties");

        AtomicInteger initialCalls = new AtomicInteger();
        ChunkExtractionService interrupted = new ChunkExtractionService("urn:graph:", text -> {
            if ( initialCalls.incrementAndGet() == 2 )
                throw new IllegalStateException("provider rate limit");
            return List.of("Alice", "Bob");
        }, (text, entities) -> List.of(), (name, findings) -> "summary");
        assertThrows(IllegalStateException.class, () -> interrupted.enrich(dataset, checkpoint));
        assertEquals(2, initialCalls.get());
        assertEquals(2, resources(dataset, GRAG.Entity));

        AtomicInteger resumedEntityCalls = new AtomicInteger();
        AtomicInteger resumedRelationshipCalls = new AtomicInteger();
        AtomicInteger resumedCommunityCalls = new AtomicInteger();
        ChunkExtractionService resumed = new ChunkExtractionService("urn:graph:", text -> {
            resumedEntityCalls.incrementAndGet();
            return text.startsWith("Alice") ? List.of("Alice", "Bob") : List.of("Bob", "Carol");
        }, (text, entities) -> {
            resumedRelationshipCalls.incrementAndGet();
            return List.of();
        }, (name, findings) -> {
            resumedCommunityCalls.incrementAndGet();
            return "summary";
        });

        resumed.enrich(dataset, checkpoint);
        assertEquals(1, resumedEntityCalls.get());
        assertEquals(2, resumedRelationshipCalls.get());
        assertEquals(3, resumedCommunityCalls.get());

        ChunkExtractionService noRepeatedCalls = new ChunkExtractionService("urn:graph:", text -> {
            throw new AssertionError("entity extraction should be restored from the checkpoint");
        }, (text, entities) -> {
            throw new AssertionError("relationship extraction should be restored from the checkpoint");
        }, (name, findings) -> {
            throw new AssertionError("community summary should be restored from the checkpoint");
        });
        noRepeatedCalls.enrich(dataset, checkpoint);
    }

    @Test
    public void enrich_discardsCheckpointWhenChunkContentChanges() {
        Path checkpoint = temporaryDirectory.resolve("extraction.properties");
        Dataset original = DatasetFactory.createTxnMem();
        addChunk(original, "urn:chunk:one", "Original content.");
        ChunkExtractionService initial = new ChunkExtractionService("urn:graph:", text -> List.of("Alice"),
                (text, entities) -> List.of(), (name, findings) -> "summary");
        initial.enrich(original, checkpoint);

        Dataset changed = DatasetFactory.createTxnMem();
        addChunk(changed, "urn:chunk:one", "Changed content.");
        AtomicInteger entityCalls = new AtomicInteger();
        ChunkExtractionService resumed = new ChunkExtractionService("urn:graph:", text -> {
            entityCalls.incrementAndGet();
            return List.of("Bob");
        }, (text, entities) -> List.of(), (name, findings) -> "summary");

        resumed.enrich(changed, checkpoint);
        assertEquals(1, entityCalls.get());
    }

    private static void addChunk(Dataset dataset, String uri, String text) {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.getDefaultModel().createResource(uri)
                    .addProperty(RDF.type, GRAG.Chunk)
                    .addProperty(GRAG.text, text);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    private static int resources(Dataset dataset, Resource type) {
        dataset.begin(ReadWrite.READ);
        try {
            return dataset.getDefaultModel().listResourcesWithProperty(RDF.type, type).toList().size();
        } finally {
            dataset.end();
        }
    }
}