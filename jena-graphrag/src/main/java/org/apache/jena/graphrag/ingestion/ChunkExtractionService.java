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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.nio.file.Path;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.graphrag.provider.CommunitySummarizer;
import org.apache.jena.graphrag.provider.EntityExtractor;
import org.apache.jena.graphrag.provider.RelationshipExtractor;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;

/**
 * Enriches ingested GraphRAG chunks with entities, relationships, and communities.
 * Successful provider responses are materialized incrementally so an interrupted extraction
 * retains its completed semantic graph work.
 */
public final class ChunkExtractionService {

    private final String baseUri;
    private final EntityExtractor entityExtractor;
    private final RelationshipExtractor relationshipExtractor;
    private final CommunitySummarizer communitySummarizer;

    public ChunkExtractionService(String baseUri, EntityExtractor entityExtractor,
            RelationshipExtractor relationshipExtractor, CommunitySummarizer communitySummarizer) {
        if (baseUri == null || baseUri.isBlank())
            throw new IllegalArgumentException("baseUri must not be blank");
        this.baseUri = baseUri;
        this.entityExtractor = Objects.requireNonNull(entityExtractor, "entityExtractor");
        this.relationshipExtractor = Objects.requireNonNull(relationshipExtractor, "relationshipExtractor");
        this.communitySummarizer = Objects.requireNonNull(communitySummarizer, "communitySummarizer");
    }

    /**
     * Extracts semantic graph resources from every textual {@code mg:Chunk} in the dataset.
     * Resource identifiers are derived from their semantic content, making repeated calls idempotent.
     *
     * @param dataset target dataset
     * @return the deterministic number of resources represented by the enrichment
     */
    public Result enrich(Dataset dataset) {
        return enrich(dataset, null);
    }

    /**
     * Enriches a dataset, reusing successful provider responses stored in an optional checkpoint file.
     *
     * @param dataset target dataset
     * @param checkpointFile optional persistent provider-response checkpoint
     * @return the deterministic number of resources represented by the enrichment
     */
    public Result enrich(Dataset dataset, Path checkpointFile) {
        return enrich(dataset, checkpointFile, ignored -> {});
    }

    /**
     * Enriches a dataset, reporting each extraction operation to an optional progress consumer.
     *
     * @param dataset target dataset
     * @param checkpointFile optional persistent provider-response checkpoint
     * @param progress receives extraction progress before each provider or checkpoint operation
     * @return the deterministic number of resources represented by the enrichment
     */
    public Result enrich(Dataset dataset, Path checkpointFile, Consumer<Progress> progress) {
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(progress, "progress");
        List<ChunkTextReader.ChunkText> chunks = ChunkTextReader.read(dataset);
        ExtractionCheckpoint checkpoint = checkpointFile == null ? null : ExtractionCheckpoint.open(checkpointFile);
        if ( checkpoint != null )
            checkpoint.prepare(chunks);
        Extraction extraction = extract(dataset, chunks, checkpoint, progress);
        writeAtomically(dataset, toModel(extraction));
        return new Result(chunks.size(), extraction.entities().size(), extraction.relationships().size(),
                extraction.communities().size());
    }

    private Extraction extract(Dataset dataset, List<ChunkTextReader.ChunkText> chunks, ExtractionCheckpoint checkpoint,
            Consumer<Progress> progress) {
        Map<String, Entity> entities = new LinkedHashMap<>();
        List<ChunkEntities> chunkEntities = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            ChunkTextReader.ChunkText chunk = chunks.get(index);
            List<Entity> mentioned = new ArrayList<>();
            List<String> names = checkpoint == null ? null : checkpoint.entities(chunk.uri()).orElse(null);
            progress.accept(new Progress(Phase.ENTITIES, index + 1, chunks.size(), names != null));
            if ( names == null ) {
                names = values(entityExtractor.extract(chunk.text()));
                if ( checkpoint != null )
                    checkpoint.entities(chunk.uri(), names);
            }
            for (String name : names) {
                String displayName = normalized(name);
                if (displayName == null)
                    continue;
                Entity entity = entities.computeIfAbsent(key(displayName), ignored -> new Entity(displayName));
                if (!mentioned.contains(entity))
                    mentioned.add(entity);
            }
            chunkEntities.add(new ChunkEntities(chunk, List.copyOf(mentioned)));
            writeAtomically(dataset, toModel(new Extraction(List.copyOf(chunkEntities), entities, Map.of(), List.of())));
        }

        Map<String, Relationship> relationships = new LinkedHashMap<>();
        for (int index = 0; index < chunkEntities.size(); index++) {
            ChunkEntities chunk = chunkEntities.get(index);
            Map<String, Entity> mentioned = new HashMap<>();
            chunk.entities().forEach(entity -> mentioned.put(key(entity.name()), entity));
            List<String> names = chunk.entities().stream().map(Entity::name).toList();
            List<RelationshipExtractor.Relationship> extractedRelationships = checkpoint == null ? null
                    : checkpoint.relationships(chunk.chunk().uri()).orElse(null);
            progress.accept(new Progress(Phase.RELATIONSHIPS, index + 1, chunkEntities.size(), extractedRelationships != null));
            if ( extractedRelationships == null ) {
                extractedRelationships = values(relationshipExtractor.extract(chunk.chunk().text(), names));
                if ( checkpoint != null )
                    checkpoint.relationships(chunk.chunk().uri(), extractedRelationships);
            }
            for (RelationshipExtractor.Relationship extracted : extractedRelationships) {
                if (extracted == null)
                    continue;
                Entity source = mentioned.get(key(extracted.source()));
                Entity target = mentioned.get(key(extracted.target()));
                if (source == null || target == null)
                    continue;
                Relationship relationship = new Relationship(chunk.chunk().uri(), source, target,
                        normalized(extracted.description()));
                relationships.putIfAbsent(relationship.id(), relationship);
            }
            writeAtomically(dataset, toModel(new Extraction(chunkEntities, entities, relationships, List.of())));
        }
        return new Extraction(chunkEntities, entities, relationships,
            communities(entities.values(), relationships.values(), checkpoint, progress));
    }

    private List<Community> communities(Collection<Entity> entities, Collection<Relationship> relationships,
            ExtractionCheckpoint checkpoint, Consumer<Progress> progress) {
        Map<Entity, Set<Entity>> adjacent = new LinkedHashMap<>();
        entities.forEach(entity -> adjacent.put(entity, new LinkedHashSet<>()));
        relationships.forEach(relationship -> {
            adjacent.get(relationship.source()).add(relationship.target());
            adjacent.get(relationship.target()).add(relationship.source());
        });

        Set<Entity> seen = new LinkedHashSet<>();
        List<Community> communities = new ArrayList<>();
        for (Entity seed : entities) {
            if (!seen.add(seed))
                continue;
            Set<Entity> members = new LinkedHashSet<>();
            collectConnected(seed, adjacent, seen, members);
            List<Entity> orderedMembers = members.stream().sorted(Comparator.comparing(Entity::name)).toList();
            List<String> findings = relationships.stream()
                    .filter(relationship -> members.contains(relationship.source()) && members.contains(relationship.target()))
                    .map(Relationship::description)
                    .filter(Objects::nonNull)
                    .toList();
            String title = orderedMembers.stream().map(Entity::name).reduce((left, right) -> left + ", " + right).orElseThrow();
            String checkpointKey = title + "\n" + String.join("\n", findings);
            String summary = checkpoint == null ? null : checkpoint.community(checkpointKey).orElse(null);
            progress.accept(new Progress(Phase.COMMUNITIES, communities.size() + 1, 0, summary != null));
            if ( summary == null ) {
                summary = Objects.requireNonNull(communitySummarizer.summarize(title, findings), "community summary");
                if ( checkpoint != null )
                    checkpoint.community(checkpointKey, summary);
            }
            communities.add(new Community(orderedMembers, findings, title, summary));
        }
        return List.copyOf(communities);
    }

    private static void collectConnected(Entity entity, Map<Entity, Set<Entity>> adjacent, Set<Entity> seen,
            Set<Entity> members) {
        members.add(entity);
        for (Entity neighbor : adjacent.get(entity)) {
            if (seen.add(neighbor))
                collectConnected(neighbor, adjacent, seen, members);
        }
    }

    private Model toModel(Extraction extraction) {
        Model model = ModelFactory.createDefaultModel();
        Map<Entity, Resource> resources = new HashMap<>();
        extraction.entities().values().forEach(entity -> resources.put(entity, model.createResource(baseUri + "entity-" + digest(entity.name()))
                .addProperty(RDF.type, GRAG.Entity)
                .addProperty(GRAG.name, entity.name())));

        extraction.chunkEntities().forEach(chunk -> {
            Resource resource = model.createResource(chunk.chunk().uri());
            chunk.entities().forEach(entity -> resource.addProperty(GRAG.hasEntity, resources.get(entity)));
        });
        extraction.relationships().values().forEach(relationship -> {
            Resource source = resources.get(relationship.source());
            Resource target = resources.get(relationship.target());
            Resource resource = model.createResource(baseUri + "relationship-" + digest(relationship.id()))
                    .addProperty(RDF.type, GRAG.Relationship)
                    .addProperty(GRAG.source, source)
                    .addProperty(GRAG.target, target)
                    .addLiteral(GRAG.weight, 1.0d);
            if (relationship.description() != null)
                resource.addProperty(GRAG.description, relationship.description());
            source.addProperty(GRAG.relatedTo, target);
        });
        extraction.communities().forEach(community -> addCommunity(model, resources, community));
        return model;
    }

    private void addCommunity(Model model, Map<Entity, Resource> resources, Community community) {
        Resource resource = model.createResource(baseUri + "community-" + digest(community.id()))
                .addProperty(RDF.type, GRAG.Community)
                .addProperty(GRAG.title, community.title())
                .addProperty(GRAG.summary, community.summary())
                .addProperty(GRAG.fullContent, community.summary());
        community.members().forEach(entity -> resources.get(entity).addProperty(GRAG.inCommunity, resource));
        Resource finding = model.createResource(baseUri + "finding-" + digest(community.id()))
                .addProperty(RDF.type, GRAG.Finding)
                .addProperty(GRAG.summary, String.join("\n", community.findings()))
                .addProperty(GRAG.fullContent, String.join("\n", community.findings()));
        resource.addProperty(GRAG.hasFinding, finding);
    }

    private static void writeAtomically(Dataset dataset, Model additions) {
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.getDefaultModel().add(additions);
            dataset.commit();
        } finally {
            dataset.end();
        }
    }

    private static <T> List<T> values(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String key(String value) {
        String normalized = normalized(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private static String digest(String value) {
        return DigestUtils.sha256Hex(value);
    }

    public record Result(int chunksSeen, int entitiesCreated, int relationshipsCreated, int communitiesCreated) {}

    public record Progress(Phase phase, int current, int total, boolean restoredFromCheckpoint) {}

    public enum Phase { ENTITIES, RELATIONSHIPS, COMMUNITIES }

    private record ChunkEntities(ChunkTextReader.ChunkText chunk, List<Entity> entities) {}
    private record Entity(String name) {}
    private record Relationship(String chunkUri, Entity source, Entity target, String description) {
        String id() { return chunkUri + "\n" + source.name() + "\n" + target.name() + "\n" + description; }
    }
    private record Community(List<Entity> members, List<String> findings, String title, String summary) {
        String id() { return members.stream().map(Entity::name).reduce((left, right) -> left + "\n" + right).orElseThrow(); }
    }
    private record Extraction(List<ChunkEntities> chunkEntities, Map<String, Entity> entities,
            Map<String, Relationship> relationships, List<Community> communities) {}
}