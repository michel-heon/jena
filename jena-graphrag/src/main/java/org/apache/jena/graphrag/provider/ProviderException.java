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

package org.apache.jena.graphrag.provider;

import java.net.http.HttpTimeoutException;
import java.util.Locale;

/** Unchecked failure raised by a GraphRAG provider. */
public class ProviderException extends RuntimeException {
    public enum Category { AUTHENTICATION, TIMEOUT, UNAVAILABLE }

    private final Category category;

    public ProviderException(String message) {
        this(Category.UNAVAILABLE, message);
    }

    public ProviderException(String message, Throwable cause) {
        this(Category.UNAVAILABLE, message, cause);
    }

    public ProviderException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public ProviderException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** Converts an implementation failure to a category-safe provider exception. */
    public static ProviderException from(Throwable cause) {
        String details = details(cause);
        if ( details.contains("401") || details.contains("403") || details.contains("unauthorized") || details.contains("forbidden") )
            return new ProviderException(Category.AUTHENTICATION, "Provider authentication failed", cause);
        if ( hasTimeout(cause) || details.contains("timed out") || details.contains("timeout") )
            return new ProviderException(Category.TIMEOUT, "Provider request timed out", cause);
        return new ProviderException(Category.UNAVAILABLE, "Provider request failed", cause);
    }

    private static boolean hasTimeout(Throwable cause) {
        for ( Throwable current = cause; current != null; current = current.getCause() ) {
            if ( current instanceof HttpTimeoutException || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException )
                return true;
        }
        return false;
    }

    private static String details(Throwable cause) {
        StringBuilder details = new StringBuilder();
        for ( Throwable current = cause; current != null; current = current.getCause() ) {
            if ( current.getMessage() != null )
                details.append(current.getMessage()).append(' ');
        }
        return details.toString().toLowerCase(Locale.ROOT);
    }
}