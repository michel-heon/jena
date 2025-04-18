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

import static org.apache.jena.atlas.lib.Lib.uppercase;

import org.apache.jena.sparql.sse.Tags;

public class E_SHA256 extends ExprDigest {
    private static final String symbol = Tags.tagSHA256;
    private static final String printName = uppercase(symbol);

    public E_SHA256(Expr expr) {
        super(expr, symbol, printName, "SHA-256");
    }

    @Override
    public Expr copy(Expr expr) {
        return new E_SHA256(expr);
    }
}
