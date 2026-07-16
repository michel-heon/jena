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

import java.nio.file.Path;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;

/**
 * Normalising importer for Microsoft GraphRAG RDF dumps.
 * <p>
 * The reference dumps produced by Microsoft GraphRAG use {@code snake_case}
 * local names (e.g. {@code has_entity}, {@code n_tokens}) and reify entity
 * relationships with the singleton-property pattern
 * ({@code d:Entity_A d:related_to_<id> d:Entity_B}, with
 * {@code d:related_to_<id> a gr:related_to}). Those conventions differ from the
 * {@code mg:} vocabulary adopted by this module, which uses {@code camelCase}
 * local names and an explicit {@link GRAG#Relationship} node.
 * <p>
 * This importer reads such a dump and writes a normalised graph into a target
 * {@link Dataset}:
 * <ul>
 *   <li>{@code snake_case} predicates/classes in the msft-graphrag namespace are
 *       rewritten to their {@code camelCase} form;</li>
 *   <li>each singleton relationship edge is rewritten to an explicit
 *       {@link GRAG#Relationship} node carrying {@link GRAG#source} /
 *       {@link GRAG#target} (plus its own attributes such as {@code mg:weight},
 *       {@code mg:rank}, {@code mg:description}), and a convenience direct edge
 *       {@code source mg:relatedTo target} is also emitted.</li>
 * </ul>
 * The importer performs no LLM calls and no network access. It reuses the source
 * individual URIs, so importing the same dump twice is idempotent (RDF set
 * semantics).
 * <p>
 * See {@code docs/adr/402-DATA-normalisation-import-mg-relationship.md}.
 */
public final class GraphRAGImporter {

    private GraphRAGImporter() {
        // utility class
    }

    /** The msft-graphrag vocabulary namespace (shared by the {@code gr:}/{@code mg:} prefixes). */
    private static final String MG = GRAG.uri;

    /**
     * Reads a GraphRAG dump from {@code source}, normalises it, and adds the
     * result to the default graph of {@code dataset}.
     *
     * @param source  path to a GraphRAG RDF dump (Turtle, TriG, ...)
     * @param dataset target dataset (a transaction is opened internally)
     * @return the number of statements written to the target graph
     */
    public static long load(Path source, Dataset dataset) {
        Model src = RDFDataMgr.loadModel(source.toString());
        return load(src, dataset);
    }

    /**
     * Normalises an already loaded {@code source} model and adds the result to
     * the default graph of {@code dataset}.
     *
     * @param source  a model holding a GraphRAG dump
     * @param dataset target dataset (a transaction is opened internally)
     * @return the number of statements written to the target graph
     */
    public static long load(Model source, Dataset dataset) {
        Model out = normalize(source);
        dataset.begin(ReadWrite.WRITE);
        try {
            dataset.getDefaultModel().add(out);
            dataset.commit();
        } finally {
            dataset.end();
        }
        return out.size();
    }

    /**
     * Produces a normalised copy of {@code source} following the {@code mg:}
     * conventions. The source model is not modified.
     *
     * @param source a model holding a GraphRAG dump
     * @return a new model with normalised statements
     */
    public static Model normalize(Model source) {
        Model out = ModelFactory.createDefaultModel();
        // A relationship-edge predicate is any resource typed as gr:related_to.
        Resource relatedToClass = source.getResource(MG + "related_to");

        StmtIterator it = source.listStatements();
        while (it.hasNext()) {
            Statement st = it.nextStatement();
            Resource s = st.getSubject();
            Property p = st.getPredicate();
            RDFNode o = st.getObject();

            if (p.equals(RDF.type)) {
                copyTypeStatement(out, s, o);
                continue;
            }

            Resource pRes = source.getResource(p.getURI());
            if (source.contains(pRes, RDF.type, relatedToClass)) {
                // Singleton-property edge: s <relationship> o.
                Resource rel = out.createResource(p.getURI());
                Resource target = o.asResource();
                out.add(rel, RDF.type, GRAG.Relationship);
                out.add(rel, GRAG.source, s);
                out.add(rel, GRAG.target, target);
                out.add(s, GRAG.relatedTo, target);
                continue;
            }

            if (p.getURI().startsWith(MG)) {
                Property mapped = out.createProperty(MG + snakeToCamel(p.getLocalName()));
                out.add(s, mapped, o);
                continue;
            }

            // Foreign predicate (e.g. dc:*): copy verbatim.
            out.add(s, p, o);
        }
        return out;
    }

    private static void copyTypeStatement(Model out, Resource s, RDFNode o) {
        if (o.isResource() && o.asResource().getURI() != null
                && o.asResource().getURI().startsWith(MG)) {
            String local = o.asResource().getLocalName();
            if ("related_to".equals(local)) {
                out.add(s, RDF.type, GRAG.Relationship);
            } else {
                // Class local names are already PascalCase in the source dumps.
                out.add(s, RDF.type, out.createResource(MG + local));
            }
        } else {
            out.add(s, RDF.type, o);
        }
    }

    /**
     * Converts a {@code snake_case} local name to {@code camelCase}. Names
     * without an underscore are returned unchanged.
     *
     * @param local a local name
     * @return the {@code camelCase} equivalent
     */
    static String snakeToCamel(String local) {
        if (local.indexOf('_') < 0) {
            return local;
        }
        String[] parts = local.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(parts[i].charAt(0)))
              .append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
