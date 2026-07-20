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

import java.nio.file.Path;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.Mode;
import org.apache.jena.assembler.assemblers.AssemblerBase;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.lib.IRILib;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import static org.apache.jena.sparql.util.graph.GraphUtils.checkExactlyOneProperty;

/** Assembles a GraphRAG Lucene text index and Lucene KNN vector index from RDF. */
public final class GraphRAGIndexAssembler extends AssemblerBase {

    @Override
    public GraphRAGIndex open(Assembler assembler, Resource root, Mode mode) {
        if ( !root.getModel().contains(null, GraphRAGAssemblerVocab.enableGraphRAG,
                root.getModel().createTypedLiteral(true)) )
            return GraphRAGIndex.disabled();

        Path textIndexDir = requiredPath(root, GraphRAGAssemblerVocab.textIndexDir);
        Path vectorIndexDir = requiredPath(root, GraphRAGAssemblerVocab.vectorIndexDir);
        int vectorDimension = optionalInt(root, GraphRAGAssemblerVocab.vectorDimension,
                GraphRAGIndex.DEFAULT_VECTOR_DIMENSION);
        return GraphRAGIndex.open(textIndexDir, vectorIndexDir, vectorDimension);
    }

    private static Path requiredPath(Resource root, org.apache.jena.rdf.model.Property property) {
        if ( !checkExactlyOneProperty(root, property) )
            throw new AssemblerException(root, "Required property missing or duplicated: " + property);
        RDFNode value = root.getProperty(property).getObject();
        if ( value.isLiteral() ) {
            String path = value.asLiteral().getLexicalForm();
            if ( path.isBlank() )
                throw new AssemblerException(root, "Property must not be blank: " + property);
            return Path.of(path);
        }
        if ( value.isURIResource() )
            return Path.of(IRILib.IRIToFilename(value.asResource().getURI()));
        throw new AssemblerException(root, "Property must be a literal path or file IRI: " + property);
    }

    private static int optionalInt(Resource root, org.apache.jena.rdf.model.Property property, int fallback) {
        if ( !root.hasProperty(property) )
            return fallback;
        if ( !checkExactlyOneProperty(root, property) )
            throw new AssemblerException(root, "Property must appear at most once: " + property);
        Statement statement = root.getProperty(property);
        RDFNode value = statement.getObject();
        if ( !value.isLiteral() )
            throw new AssemblerException(root, "Property must be an integer literal: " + property);
        try {
            int parsed = value.asLiteral().getInt();
            if ( parsed < 1 )
                throw new AssemblerException(root, "Property must be a positive integer: " + property);
            return parsed;
        } catch (RuntimeException ex) {
            throw new AssemblerException(root, "Property must be an integer literal: " + property, ex);
        }
    }
}