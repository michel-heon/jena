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
 * Immutable configuration for the PDF document ingestion pipeline.
 * Validated eagerly in the compact constructor so an invalid config
 * is detected before any I/O or RDF write.
 * <p>
 * Defined in ADR-403.
 */
public record DocumentIngestionConfig(
        String  baseUri,
        int     chunkSize,
        int     chunkOverlap,
        long    maxFileSizeBytes) {

    /** Default base URI for generated document and chunk URIs. */
    public static final String DEFAULT_BASE_URI           = "http://ormynet.com/ns/data#";
    /** Default chunk window size in characters. */
    public static final int    DEFAULT_CHUNK_SIZE         = 1_000;
    /** Default overlap between adjacent chunk windows in characters. */
    public static final int    DEFAULT_CHUNK_OVERLAP      = 200;
    /** Default maximum file size accepted (50 MB). */
    public static final long   DEFAULT_MAX_FILE_SIZE_BYTES = 50L * 1_024 * 1_024;
    /** Minimum allowed chunk size in characters. */
    public static final int    MIN_CHUNK_SIZE             = 50;

    public DocumentIngestionConfig {
        if (baseUri == null || baseUri.isBlank())
            throw new IllegalArgumentException("baseUri must not be blank");
        if (chunkSize < MIN_CHUNK_SIZE)
            throw new IllegalArgumentException(
                    "chunkSize must be >= " + MIN_CHUNK_SIZE + ", got: " + chunkSize);
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize)
            throw new IllegalArgumentException(
                    "chunkOverlap must satisfy 0 <= chunkOverlap < chunkSize, "
                    + "got: overlap=" + chunkOverlap + " size=" + chunkSize);
        if (maxFileSizeBytes <= 0)
            throw new IllegalArgumentException(
                    "maxFileSizeBytes must be > 0, got: " + maxFileSizeBytes);
    }

    /** Returns a config with default values. */
    public static DocumentIngestionConfig defaults() {
        return new DocumentIngestionConfig(
                DEFAULT_BASE_URI,
                DEFAULT_CHUNK_SIZE,
                DEFAULT_CHUNK_OVERLAP,
                DEFAULT_MAX_FILE_SIZE_BYTES);
    }
}
