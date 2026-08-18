/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.graphrag.ingestion;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.jena.graphrag.index.CommunityReportVectorIndexer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.GRAG;
import org.apache.jena.vocabulary.RDF;

/** Vectorizes existing {@code mg:Community} reports using their summary and full content. */
public final class CommunityReportVectorizationService {

    private final CommunityReportVectorIndexer communityVectorIndexer;

    public CommunityReportVectorizationService(CommunityReportVectorIndexer communityVectorIndexer) {
        this.communityVectorIndexer = Objects.requireNonNull(communityVectorIndexer, "communityVectorIndexer");
    }

    public Result vectorize(Dataset dataset) {
        Objects.requireNonNull(dataset, "dataset");
        dataset.begin(ReadWrite.READ);
        try {
            List<Resource> communities;
            StmtIterator statements = dataset.getDefaultModel().listStatements(null, RDF.type, GRAG.Community);
            try {
                communities = statements.toList().stream()
                        .map(Statement::getSubject)
                        .filter(Resource::isURIResource)
                        .distinct()
                        .sorted(Comparator.comparing(Resource::getURI))
                        .toList();
            } finally {
                statements.close();
            }
            int indexed = 0;
            int alreadyIndexed = 0;
            int skipped = 0;
            for ( Resource community : communities ) {
                try {
                    if ( communityVectorIndexer.indexCommunity(community.getURI(), literalValue(community, GRAG.summary),
                            literalValue(community, GRAG.fullContent)) )
                        indexed++;
                    else
                        alreadyIndexed++;
                } catch (IllegalArgumentException ex) {
                    if ( ex.getMessage().equals("community report must contain a summary or full content") )
                        skipped++;
                    else
                        throw ex;
                }
            }
            return new Result(communities.size(), indexed, alreadyIndexed, skipped);
        } finally {
            dataset.end();
        }
    }

    private static String literalValue(Resource resource, org.apache.jena.rdf.model.Property property) {
        Statement statement = resource.getProperty(property);
        return statement != null && statement.getObject().isLiteral() ? statement.getString() : null;
    }

    public record Result(int communitiesSeen, int communitiesIndexed, int communitiesAlreadyIndexed,
                         int communitiesWithoutReport) {}
}