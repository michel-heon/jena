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

    /** The namespace of the GRAG vocabulary. */
    public static final Resource NAMESPACE = m.createResource(uri);

    /* ##########################################################
     * Defines GRAG Classes
     * (from msft-graphrag.ttl)
     ########################################################## */

    public static final Resource Document  = m.createResource(uri + "Document");
    public static final Resource Chunk     = m.createResource(uri + "Chunk");
    public static final Resource Entity    = m.createResource(uri + "Entity");
    public static final Resource Community = m.createResource(uri + "Community");
    public static final Resource Covariate = m.createResource(uri + "Covariate");
    public static final Resource Finding   = m.createResource(uri + "Finding");

    /* ##########################################################
     * Defines GRAG Properties
     * (from msft-graphrag.ttl)
     ########################################################## */

    // Object properties
    public static final Property hasCovariate = m.createProperty(uri + "hasCovariate");
    public static final Property hasFinding   = m.createProperty(uri + "hasFinding");
    public static final Property inCommunity  = m.createProperty(uri + "inCommunity");
    public static final Property relatedTo    = m.createProperty(uri + "relatedTo");
    public static final Property hasEntity    = m.createProperty(uri + "hasEntity");
    public static final Property partOf       = m.createProperty(uri + "partOf");

    // Datatype properties
    public static final Property documentIds         = m.createProperty(uri + "documentIds");
    public static final Property textUnitId          = m.createProperty(uri + "textUnitId");
    public static final Property finding             = m.createProperty(uri + "finding");
    public static final Property summary             = m.createProperty(uri + "summary");
    public static final Property rankExplanation     = m.createProperty(uri + "rankExplanation");
    public static final Property level               = m.createProperty(uri + "level");
    public static final Property fullContent         = m.createProperty(uri + "fullContent");
    public static final Property rank                = m.createProperty(uri + "rank");
    public static final Property descriptionEmbedding = m.createProperty(uri + "descriptionEmbedding");
    public static final Property humanReadableId     = m.createProperty(uri + "humanReadableId");
    public static final Property description         = m.createProperty(uri + "description");
    public static final Property name                = m.createProperty(uri + "name");
    public static final Property text                = m.createProperty(uri + "text");
    public static final Property nTokens             = m.createProperty(uri + "nTokens");
    public static final Property id                  = m.createProperty(uri + "id");
    public static final Property title               = m.createProperty(uri + "title");
}
