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

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Vocabulary definition for the Microsoft GraphRAG RDF schema.
 * <p>
 * Terms are derived from the Microsoft GraphRAG ontology file
 * {@code msft-graphrag.ttl} (namespace {@code http://ormynet.com/ns/msft-graphrag#}).
 */
public class GRAG {

    /** The namespace of the GRAG vocabulary as a string. */
    public static final String NS = "http://ormynet.com/ns/msft-graphrag#";

    /** Backward-compatible alias for Jena vocabulary classes that still expose {@code uri}. */
    public static final String uri = NS;

    /**
     * Returns the namespace of the GRAG vocabulary as a string.
     *
     * @return the namespace of the GRAG vocabulary
     */
    public static String getURI() {
        return NS;
    }

    protected static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    protected static Property property(String local) {
        return ResourceFactory.createProperty(NS, local);
    }

    /** RDF resource representing the GraphRAG vocabulary namespace. */
    public static final Resource NAMESPACE = resource("");

    /** GraphRAG source document resource class. */
    public static final Resource Document  = resource("Document");
    /** Text chunk resource class used for passages ready for retrieval/indexing. */
    public static final Resource Chunk     = resource("Chunk");
    /** Entity resource class extracted or imported from a GraphRAG knowledge graph. */
    public static final Resource Entity    = resource("Entity");
    /** Community resource class for GraphRAG global/community context. */
    public static final Resource Community = resource("Community");
    /** Covariate or claim resource class associated with an entity. */
    public static final Resource Covariate = resource("Covariate");
    /** Finding resource class for community report observations. */
    public static final Resource Finding   = resource("Finding");

    /**
     * Reified relationship edge (Apache Jena extension term, not part of the
     * upstream Ormesher OWL ontology). See
     * {@code docs/adr/402-DATA-normalisation-import-mg-relationship.md}.
     */
    public static final Resource Relationship = resource("Relationship");

    /** Links an entity to an associated covariate or claim. */
    public static final Property hasCovariate = property("hasCovariate");
    /** Links a community report to one of its findings. */
    public static final Property hasFinding   = property("hasFinding");
    /** Links an entity to the community that contains it. */
    public static final Property inCommunity  = property("inCommunity");
    /** Direct entity-to-entity edge emitted for simple SPARQL traversal. */
    public static final Property relatedTo    = property("relatedTo");
    /** Links a chunk to an entity mentioned in that chunk. */
    public static final Property hasEntity    = property("hasEntity");
    /** Links a chunk to its source document. */
    public static final Property partOf       = property("partOf");

    /** Relationship endpoint pointing to the source entity. */
    public static final Property source       = property("source");
    /** Relationship endpoint pointing to the target entity. */
    public static final Property target       = property("target");

    /** External source document identifiers carried by imported GraphRAG data. */
    public static final Property documentIds         = property("documentIds");
    /** External text unit identifier carried by imported GraphRAG data. */
    public static final Property textUnitId          = property("textUnitId");
    /** Textual finding content in a community report. */
    public static final Property finding             = property("finding");
    /** Summary text for a community or report resource. */
    public static final Property summary             = property("summary");
    /** Explanation associated with a rank value. */
    public static final Property rankExplanation     = property("rankExplanation");
    /** Hierarchical community level. */
    public static final Property level               = property("level");
    /** Full textual content of a report-like GraphRAG resource. */
    public static final Property fullContent         = property("fullContent");
    /** Rank value imported from GraphRAG data. */
    public static final Property rank                = property("rank");
    /** Serialized description embedding when present in imported data. */
    public static final Property descriptionEmbedding = property("descriptionEmbedding");
    /** Human-readable identifier from imported GraphRAG data. */
    public static final Property humanReadableId     = property("humanReadableId");
    /** Description text for an entity, relation, or report resource. */
    public static final Property description         = property("description");
    /** Display name for an entity or related resource. */
    public static final Property name                = property("name");
    /** Main text content of a chunk or text unit. */
    public static final Property text                = property("text");
    /** Token count metadata from imported GraphRAG data. */
    public static final Property nTokens             = property("nTokens");
    /** External identifier from imported GraphRAG data. */
    public static final Property id                  = property("id");
    /** Title text for imported GraphRAG resources that provide one. */
    public static final Property title               = property("title");

    /** Relationship weight found in real dumps and preserved by ADR-402 normalization. */
    public static final Property weight              = property("weight");
    /** Type literal found in real dumps and preserved during normalization. */
    public static final Property type                = property("type");

    /** SHA-256 hash of the ingested source document bytes. */
    public static final Property sourceHash  = property("sourceHash");
    /** Source file name recorded for PDF ingestion traceability. */
    public static final Property sourceFile  = property("sourceFile");
    /** Zero-based chunk index within an ingested source document. */
    public static final Property chunkIndex  = property("chunkIndex");
    /** Page or page range covered by an ingested PDF chunk. */
    public static final Property chunkPages  = property("chunkPages");
}
