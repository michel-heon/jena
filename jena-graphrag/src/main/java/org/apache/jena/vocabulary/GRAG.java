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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Vocabulary definition for the Microsoft GraphRAG RDF schema.
 * <p>
 * Terms are derived from the Microsoft GraphRAG ontology file
 * {@code msft-graphrag.ttl} (namespace {@code http://ormynet.com/ns/msft-graphrag#}).
 */
public class GRAG {

    /** The RDF model that holds the GRAG entities. */
    private static final Model m = ModelFactory.createDefaultModel();

    /** The namespace of the GRAG vocabulary as a string. */
    public static final String uri = "http://ormynet.com/ns/msft-graphrag#";

    /**
     * Returns the namespace of the GRAG vocabulary as a string.
     *
     * @return the namespace of the GRAG vocabulary
     */
    public static String getURI() {
        return uri;
    }

    /** RDF resource representing the GraphRAG vocabulary namespace. */
    public static final Resource NAMESPACE = m.createResource(uri);

    /** GraphRAG source document resource class. */
    public static final Resource Document  = m.createResource(uri + "Document");
    /** Text chunk resource class used for passages ready for retrieval/indexing. */
    public static final Resource Chunk     = m.createResource(uri + "Chunk");
    /** Entity resource class extracted or imported from a GraphRAG knowledge graph. */
    public static final Resource Entity    = m.createResource(uri + "Entity");
    /** Community resource class for GraphRAG global/community context. */
    public static final Resource Community = m.createResource(uri + "Community");
    /** Covariate or claim resource class associated with an entity. */
    public static final Resource Covariate = m.createResource(uri + "Covariate");
    /** Finding resource class for community report observations. */
    public static final Resource Finding   = m.createResource(uri + "Finding");

    /**
     * Reified relationship edge (Apache Jena extension term, not part of the
     * upstream Ormesher OWL ontology). See
     * {@code docs/adr/402-DATA-normalisation-import-mg-relationship.md}.
     */
    public static final Resource Relationship = m.createResource(uri + "Relationship");

    /** Links an entity to an associated covariate or claim. */
    public static final Property hasCovariate = m.createProperty(uri + "hasCovariate");
    /** Links a community report to one of its findings. */
    public static final Property hasFinding   = m.createProperty(uri + "hasFinding");
    /** Links an entity to the community that contains it. */
    public static final Property inCommunity  = m.createProperty(uri + "inCommunity");
    /** Direct entity-to-entity edge emitted for simple SPARQL traversal. */
    public static final Property relatedTo    = m.createProperty(uri + "relatedTo");
    /** Links a chunk to an entity mentioned in that chunk. */
    public static final Property hasEntity    = m.createProperty(uri + "hasEntity");
    /** Links a chunk to its source document. */
    public static final Property partOf       = m.createProperty(uri + "partOf");

    /** Relationship endpoint pointing to the source entity. */
    public static final Property source       = m.createProperty(uri + "source");
    /** Relationship endpoint pointing to the target entity. */
    public static final Property target       = m.createProperty(uri + "target");

    /** External source document identifiers carried by imported GraphRAG data. */
    public static final Property documentIds         = m.createProperty(uri + "documentIds");
    /** External text unit identifier carried by imported GraphRAG data. */
    public static final Property textUnitId          = m.createProperty(uri + "textUnitId");
    /** Textual finding content in a community report. */
    public static final Property finding             = m.createProperty(uri + "finding");
    /** Summary text for a community or report resource. */
    public static final Property summary             = m.createProperty(uri + "summary");
    /** Explanation associated with a rank value. */
    public static final Property rankExplanation     = m.createProperty(uri + "rankExplanation");
    /** Hierarchical community level. */
    public static final Property level               = m.createProperty(uri + "level");
    /** Full textual content of a report-like GraphRAG resource. */
    public static final Property fullContent         = m.createProperty(uri + "fullContent");
    /** Rank value imported from GraphRAG data. */
    public static final Property rank                = m.createProperty(uri + "rank");
    /** Serialized description embedding when present in imported data. */
    public static final Property descriptionEmbedding = m.createProperty(uri + "descriptionEmbedding");
    /** Human-readable identifier from imported GraphRAG data. */
    public static final Property humanReadableId     = m.createProperty(uri + "humanReadableId");
    /** Description text for an entity, relation, or report resource. */
    public static final Property description         = m.createProperty(uri + "description");
    /** Display name for an entity or related resource. */
    public static final Property name                = m.createProperty(uri + "name");
    /** Main text content of a chunk or text unit. */
    public static final Property text                = m.createProperty(uri + "text");
    /** Token count metadata from imported GraphRAG data. */
    public static final Property nTokens             = m.createProperty(uri + "nTokens");
    /** External identifier from imported GraphRAG data. */
    public static final Property id                  = m.createProperty(uri + "id");
    /** Title text for imported GraphRAG resources that provide one. */
    public static final Property title               = m.createProperty(uri + "title");

    /** Relationship weight found in real dumps and preserved by ADR-402 normalization. */
    public static final Property weight              = m.createProperty(uri + "weight");
    /** Type literal found in real dumps and preserved during normalization. */
    public static final Property type                = m.createProperty(uri + "type");

    /** SHA-256 hash of the ingested source document bytes. */
    public static final Property sourceHash  = m.createProperty(uri + "sourceHash");
    /** Source file name recorded for PDF ingestion traceability. */
    public static final Property sourceFile  = m.createProperty(uri + "sourceFile");
    /** Zero-based chunk index within an ingested source document. */
    public static final Property chunkIndex  = m.createProperty(uri + "chunkIndex");
    /** Page or page range covered by an ingested PDF chunk. */
    public static final Property chunkPages  = m.createProperty(uri + "chunkPages");
}
