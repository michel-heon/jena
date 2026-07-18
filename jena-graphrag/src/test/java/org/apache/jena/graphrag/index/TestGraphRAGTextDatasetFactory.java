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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;

public class TestGraphRAGTextDatasetFactory {

    @Test
    public void textQuery_returnsChunkIndexedByMgText() {
        Dataset base = DatasetFactory.createTxnMem();
        Dataset dataset = GraphRAGTextDatasetFactory.createChunkTextDataset(base, new ByteBuffersDirectory());

        dataset.begin(ReadWrite.WRITE);
        try {
            Resource chunk = dataset.getDefaultModel().createResource("urn:chunk:1");
            chunk.addProperty(RDF.type, GRAG.Chunk)
                 .addProperty(GRAG.text, "Scrooge saw Marley in the counting-house.");
            dataset.commit();
        } finally {
            dataset.end();
        }

        dataset.begin(ReadWrite.READ);
        try (QueryExecution qexec = QueryExecution.dataset(dataset).query("""
                PREFIX text: <http://jena.apache.org/text#>
                PREFIX mg:   <http://ormynet.com/ns/msft-graphrag#>

                SELECT ?chunk ?score ?literal WHERE {
                  (?chunk ?score ?literal) text:query (mg:text 'Marley' 10) .
                  ?chunk a mg:Chunk .
                }
                """).build()) {
            ResultSet results = qexec.execSelect();
            assertTrue(results.hasNext(), "text:query must find the indexed chunk");
            var solution = results.next();
            assertEquals("urn:chunk:1", solution.getResource("chunk").getURI());
            assertTrue(solution.getLiteral("score").getDouble() > 0.0);
            assertEquals("Scrooge saw Marley in the counting-house.", solution.getLiteral("literal").getString());
        } finally {
            dataset.end();
        }
    }
}