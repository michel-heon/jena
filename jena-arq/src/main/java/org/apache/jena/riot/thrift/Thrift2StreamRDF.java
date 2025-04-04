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

package org.apache.jena.riot.thrift;

import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.system.FactoryRDFCaching;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.thrift.wire.RDF_PrefixDecl;
import org.apache.jena.riot.thrift.wire.RDF_Quad;
import org.apache.jena.riot.thrift.wire.RDF_Triple;
import org.apache.jena.sparql.core.Quad;

/** Thrift RDF (wire format) to RDF terms (Jena java objects)
 *
 * @see StreamRDF2Thrift for the reverse process.
 */

public class Thrift2StreamRDF implements VisitorStreamRowTRDF {

    private final StreamRDF dest;
    private final PrefixMap pmap;
    private final Cache<String, Node> uriCache =
            CacheFactory.createSimpleCache(FactoryRDFCaching.DftNodeCacheSize);

    public Thrift2StreamRDF(PrefixMap pmap, StreamRDF stream) {
        this.pmap = pmap;
        this.dest = stream;
    }

    @Override
    public void visit(RDF_Triple rt) {
        Triple t = ThriftConvert.convert(uriCache, rt, pmap);
        dest.triple(t);
    }

    @Override
    public void visit(RDF_Quad rq) {
        Quad q = ThriftConvert.convert(uriCache, rq, pmap);
        dest.quad(q);
    }

    @Override
    public void visit(RDF_PrefixDecl prefixDecl) {
        String prefix = prefixDecl.getPrefix();
        String iriStr = prefixDecl.getUri();
        pmap.add(prefix, iriStr);
        dest.prefix(prefix, iriStr);
    }
}
