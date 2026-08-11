/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.ingestion;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.graphrag.provider.RelationshipExtractor;

/** Persists successful extraction-provider responses so interrupted enrichment can resume without repeated calls. */
final class ExtractionCheckpoint {
    private static final String ENTITIES = "entities.";
    private static final String RELATIONSHIPS = "relationships.";
    private static final String COMMUNITIES = "communities.";
    private static final String CORPUS_FINGERPRINT = "corpus.fingerprint";

    private final Path file;
    private final Properties values = new Properties();

    private ExtractionCheckpoint(Path file) {
        this.file = file;
        if ( Files.exists(file) ) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read extraction checkpoint", ex);
            }
        }
    }

    static ExtractionCheckpoint open(Path file) {
        return new ExtractionCheckpoint(file);
    }

    void prepare(List<ChunkTextReader.ChunkText> chunks) {
        String fingerprint = DigestUtils.sha256Hex(chunks.stream()
                .map(chunk -> chunk.uri() + "\u0000" + chunk.text())
                .collect(java.util.stream.Collectors.joining("\u0000")));
        if ( fingerprint.equals(values.getProperty(CORPUS_FINGERPRINT)) )
            return;
        values.clear();
        values.setProperty(CORPUS_FINGERPRINT, fingerprint);
        save();
    }

    Optional<List<String>> entities(String chunkUri) {
        return list(ENTITIES, chunkUri);
    }

    void entities(String chunkUri, List<String> entities) {
        list(ENTITIES, chunkUri, entities);
    }

    Optional<List<RelationshipExtractor.Relationship>> relationships(String chunkUri) {
        String value = values.getProperty(RELATIONSHIPS + encode(chunkUri));
        if ( value == null )
            return Optional.empty();
        return Optional.of(value.isEmpty() ? List.of() : List.of(value.split(",", -1)).stream()
                .map(this::relationship).toList());
    }

    void relationships(String chunkUri, List<RelationshipExtractor.Relationship> relationships) {
        values.setProperty(RELATIONSHIPS + encode(chunkUri), relationships.stream().map(this::encode).collect(java.util.stream.Collectors.joining(",")));
        save();
    }

    Optional<String> community(String key) {
        String value = values.getProperty(COMMUNITIES + encode(key));
        return value == null ? Optional.empty() : Optional.of(decode(value));
    }

    void community(String key, String summary) {
        values.setProperty(COMMUNITIES + encode(key), encode(summary));
        save();
    }

    private Optional<List<String>> list(String prefix, String key) {
        String value = values.getProperty(prefix + encode(key));
        return value == null ? Optional.empty() : Optional.of(value.isEmpty() ? List.of()
                : List.of(value.split(",", -1)).stream().map(ExtractionCheckpoint::decode).toList());
    }

    private void list(String prefix, String key, List<String> entries) {
        values.setProperty(prefix + encode(key), entries.stream().map(ExtractionCheckpoint::encode)
                .collect(java.util.stream.Collectors.joining(",")));
        save();
    }

    private RelationshipExtractor.Relationship relationship(String value) {
        String[] parts = value.split(":", -1);
        if ( parts.length != 3 )
            throw new IllegalStateException("Invalid relationship extraction checkpoint");
        return new RelationshipExtractor.Relationship(decode(parts[0]), decode(parts[1]), nullableValue(parts[2]));
    }

    private String encode(RelationshipExtractor.Relationship relationship) {
        return String.join(":", encode(relationship.source()), encode(relationship.target()), nullable(relationship.description()));
    }

    private void save() {
        try {
            Path directory = file.toAbsolutePath().getParent();
            if ( directory != null )
                Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "GraphRAG extraction checkpoint");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write extraction checkpoint", ex);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String nullable(String value) {
        return value == null ? "!" : "." + encode(value);
    }

    private static String nullableValue(String value) {
        if ( "!".equals(value) )
            return null;
        if ( !value.startsWith(".") )
            throw new IllegalStateException("Invalid relationship extraction checkpoint");
        return decode(value.substring(1));
    }
}