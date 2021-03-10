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

package org.apache.jena.sparql.expr;

import org.apache.jena.sparql.function.library.triple.EmbeddedTripleFunctions;
import org.apache.jena.sparql.sse.Tags ;

public class E_TripleObject extends ExprFunction1
{
    private static final String symbol = Tags.tagObject;

    public E_TripleObject(Expr expr) {
        super(expr, "object", symbol) ;
    }

    @Override
    public NodeValue eval(NodeValue nv) {
        return EmbeddedTripleFunctions.tripleObject(nv);
    }

    @Override
    public Expr copy(Expr expr) {
        return new E_TripleObject(expr);
    }
}
