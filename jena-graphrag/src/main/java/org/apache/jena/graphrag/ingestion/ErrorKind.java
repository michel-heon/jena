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
 * Discriminated error kinds for {@link IngestionException}.
 * Defined in ADR-403.
 */
public enum ErrorKind {
    /** File does not start with {@code %PDF-} magic bytes. */
    INVALID_FORMAT,
    /** File size exceeds {@link DocumentIngestionConfig#maxFileSizeBytes()}. */
    FILE_TOO_LARGE,
    /** PDF is password-protected and cannot be decrypted without a key. */
    ENCRYPTED,
    /** No readable text could be extracted from the PDF. */
    NO_TEXT,
    /** Jena transactional write failed while adding ingestion triples. */
    TRANSACTION_FAILED
}
