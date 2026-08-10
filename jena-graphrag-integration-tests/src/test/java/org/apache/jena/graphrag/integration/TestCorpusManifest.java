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

package org.apache.jena.graphrag.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.GRAG;
import org.junit.jupiter.api.Test;

public class TestCorpusManifest {
    private static final String CORPUS_ROOT = "corpus/";
    private static final List<String> REQUIRED_FIELDS = List.of(
            "id", "path", "provenance", "license", "sha256", "facts", "scenarios", "ingestion");

    @Test
    public void manifest_entriesAreCompleteAndChecksummed() throws IOException, NoSuchAlgorithmException {
        Properties manifest = readManifest();
        List<String> fixtures = List.of(manifest.getProperty("corpus.files").split(","));

        assertEquals(16, fixtures.size());
        for (String fixtureName : fixtures) {
            for (String requiredField : REQUIRED_FIELDS) {
                assertTrue(manifest.containsKey(fixtureName + "." + requiredField),
                        () -> "Missing " + requiredField + " for " + fixtureName);
            }
            String resourcePath = manifest.getProperty(fixtureName + ".path");
            try (InputStream resource = resource(resourcePath)) {
                assertEquals(manifest.getProperty(fixtureName + ".sha256"), sha256(resource));
            }
        }
    }

    @Test
    public void validFixturesContainDocumentsAndChunks() {
        assertContainsDocumentAndChunk("ingestion/team-graph.ttl");
        assertContainsDocumentAndChunk("chat/citation-graph.ttl");
    }

    @Test
    public void pdfFixturesHavePdfSignature() throws IOException {
        Properties manifest = readManifest();
        for (String fixtureName : manifest.getProperty("corpus.files").split(",")) {
            String resourcePath = manifest.getProperty(fixtureName + ".path");
            if ( resourcePath.endsWith(".pdf") ) {
                try (InputStream resource = resource(resourcePath)) {
                    assertEquals("%PDF", new String(resource.readNBytes(4)));
                }
            }
        }
    }

    @Test
    public void invalidFixtureIsRejectedByTurtleParser() {
        assertThrows(RiotException.class,
                () -> RDFDataMgr.loadModel(resourceUrl("invalid/not-turtle.ttl").toString()));
    }

    private static void assertContainsDocumentAndChunk(String relativePath) {
        Model model = RDFDataMgr.loadModel(resourceUrl(relativePath).toString());
        assertTrue(model.contains(null, org.apache.jena.vocabulary.RDF.type, GRAG.Document));
        assertTrue(model.contains(null, org.apache.jena.vocabulary.RDF.type, GRAG.Chunk));
        assertTrue(model.contains(null, GRAG.partOf));
    }

    private static Properties readManifest() throws IOException {
        Properties properties = new Properties();
        try (InputStream resource = resource("manifest.properties")) {
            properties.load(resource);
        }
        return properties;
    }

    private static InputStream resource(String relativePath) {
        InputStream resource = TestCorpusManifest.class.getClassLoader().getResourceAsStream(CORPUS_ROOT + relativePath);
        assertNotNull(resource, () -> "Missing corpus resource: " + relativePath);
        return resource;
    }

    private static java.net.URL resourceUrl(String relativePath) {
        java.net.URL resource = TestCorpusManifest.class.getClassLoader().getResource(CORPUS_ROOT + relativePath);
        assertNotNull(resource, () -> "Missing corpus resource: " + relativePath);
        return resource;
    }

    private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ( (bytesRead = input.read(buffer)) != -1 ) {
            digest.update(buffer, 0, bytesRead);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}