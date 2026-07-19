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

import java.util.Objects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndex;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.vocabulary.GRAG;
import org.apache.lucene.store.Directory;

/** Factory methods for GraphRAG text datasets backed by {@code jena-text}. */
public final class GraphRAGTextDatasetFactory {

    private GraphRAGTextDatasetFactory() {}

    /**
     * Wraps a base dataset with a Lucene text index over {@code mg:Chunk}/{@code mg:text}.
     *
     * @param base base RDF dataset to wrap
     * @param directory Lucene directory used for the text index
     * @return dataset whose writes maintain the configured text index
     */
    public static Dataset createChunkTextDataset(Dataset base, Directory directory) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(directory, "directory");

        TextIndexConfig config = new TextIndexConfig(chunkEntityDefinition());
        config.setValueStored(true);
        TextIndex textIndex = TextDatasetFactory.createLuceneIndex(directory, config);
        return TextDatasetFactory.create(base, textIndex, true);
    }

    /** Returns the entity map used for {@code mg:Chunk}/{@code mg:text} indexing. */
    public static EntityDefinition chunkEntityDefinition() {
        EntityDefinition definition = new EntityDefinition("uri", "text");
        definition.set("text", GRAG.text.asNode());
        definition.setUidField("uid");
        definition.setLangField("lang");
        return definition;
    }
}