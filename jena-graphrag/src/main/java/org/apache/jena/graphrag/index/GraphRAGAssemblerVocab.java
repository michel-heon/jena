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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/** GraphRAG Fuseki assembler vocabulary ({@code grag:}). */
public final class GraphRAGAssemblerVocab {
    private static final Model model = ModelFactory.createDefaultModel();

    /** Namespace for GraphRAG Fuseki configuration and assembler terms. */
    public static final String uri = "https://jena.apache.org/graphrag/vocab#";

    /** RDF type for a configured GraphRAG text/vector index pair. */
    public static final Resource GraphRAGIndex = model.createResource(uri + "GraphRAGIndex");
    /** RDF type for the built-in deterministic embedding provider. */
    public static final Resource MockEmbeddingProvider = model.createResource(uri + "MockEmbeddingProvider");
    /** RDF type for the built-in deterministic chat provider. */
    public static final Resource MockChatCompletionProvider = model.createResource(uri + "MockChatCompletionProvider");
    /** RDF type for an opt-in OpenAI-compatible embedding provider. */
    public static final Resource HttpEmbeddingProvider = model.createResource(uri + "HttpEmbeddingProvider");
    /** RDF type for an opt-in OpenAI-compatible chat provider. */
    public static final Resource HttpChatCompletionProvider = model.createResource(uri + "HttpChatCompletionProvider");

    /** Enables GraphRAG extensions explicitly. */
    public static final Property enableGraphRAG = model.createProperty(uri + "enableGraphRAG");
    /** Links a Fuseki service/config resource to a {@code grag:GraphRAGIndex}. */
    public static final Property graphragIndex = model.createProperty(uri + "graphragIndex");
    /** Filesystem directory for the Lucene text index. */
    public static final Property textIndexDir = model.createProperty(uri + "textIndexDir");
    /** Filesystem directory for the Lucene KNN vector index. */
    public static final Property vectorIndexDir = model.createProperty(uri + "vectorIndexDir");
    /** Optional vector dimension for the Lucene KNN index. */
    public static final Property vectorDimension = model.createProperty(uri + "vectorDimension");
    /** Optional assembled embedding provider; defaults to the deterministic mock. */
    public static final Property embeddingProvider = model.createProperty(uri + "embeddingProvider");
    /** Optional assembled chat provider; defaults to the deterministic mock. */
    public static final Property chatProvider = model.createProperty(uri + "chatProvider");
    /** Explicitly permits external provider calls; false when absent. */
    public static final Property allowExternalCalls = model.createProperty(uri + "allowExternalCalls");
    /** Provider HTTP endpoint URI. */
    public static final Property endpoint = model.createProperty(uri + "endpoint");
    /** Provider model identifier. */
    public static final Property modelName = model.createProperty(uri + "modelName");
    /** Name of the environment variable containing the API key. */
    public static final Property apiKeyEnv = model.createProperty(uri + "apiKeyEnv");
    /** Provider request timeout in seconds. */
    public static final Property timeoutSeconds = model.createProperty(uri + "timeoutSeconds");
    /** Maximum estimated input tokens per provider request. */
    public static final Property maxTokensPerRequest = model.createProperty(uri + "maxTokensPerRequest");

    private GraphRAGAssemblerVocab() {}
}