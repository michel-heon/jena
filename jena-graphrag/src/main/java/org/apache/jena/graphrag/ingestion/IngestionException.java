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

package org.apache.jena.graphrag.ingestion;

/**
 * Unchecked exception raised by the PDF ingestion pipeline.
 * Carries an {@link ErrorKind} so callers can discriminate failure modes
 * without inspecting message strings.
 * <p>
 * Defined in ADR-403.
 */
public class IngestionException extends RuntimeException {

    private final ErrorKind kind;

    /**
     * Creates a structured ingestion failure without an underlying cause.
     *
     * @param kind stable machine-readable failure category
     * @param message human-readable diagnostic message
     */
    public IngestionException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Creates a structured ingestion failure wrapping the underlying exception.
     *
     * @param kind stable machine-readable failure category
     * @param message human-readable diagnostic message
     * @param cause lower-level cause that triggered ingestion failure
     */
    public IngestionException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * Returns the stable error category for callers and tests.
     *
     * @return ingestion failure kind
     */
    public ErrorKind getKind() {
        return kind;
    }
}
