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

package org.apache.jena.graphrag.fuseki;

import java.util.Set;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

/**
 * Fuseki SPI module that registers optional GraphRAG HTTP operations.
 * <p>
 * Registration is opt-in: no processor is added unless the Fuseki configuration
 * model contains {@code grag:enableGraphRAG true}. The current processor exposes
 * the delivered local context endpoint only.
 */
public final class GraphRAGModule implements FusekiModule {

    /** Namespace for GraphRAG Fuseki configuration terms such as {@code enableGraphRAG}. */
    public static final String CONFIG_NS = "https://jena.apache.org/graphrag/vocab#";

    /** Constructor used by Java SPI; configuration remains opt-in. */
    public GraphRAGModule() {}

    @Override
    public String name() {
        return "GraphRAG";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        if ( !isEnabled(configModel) )
            return;
        GraphRAGConfiguration configuration = GraphRAGConfiguration.fromModel(configModel);
        datasetNames.forEach(name -> builder.addProcessor(
                name + "/graphrag/context", new GraphRAGContextAction(builder.getDataset(name), configuration)));
    }

    /**
     * Tests whether the configuration model explicitly enables GraphRAG.
     *
     * @param configModel Fuseki configuration model, or {@code null}
     * @return {@code true} only when {@code grag:enableGraphRAG true} is present
     */
    static boolean isEnabled(Model configModel) {
        if ( configModel == null )
            return false;
        Property enabled = configModel.createProperty(CONFIG_NS + "enableGraphRAG");
        return configModel.contains(null, enabled, configModel.createTypedLiteral(true));
    }
}