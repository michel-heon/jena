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
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.jena.graphrag.fuseki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graphrag.ingestion.PdfTestFixtures;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestGraphRAGUploadEndpoint {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @TempDir Path tempDir;

    @Test
    public void post_validPdfIngestsRdfAndReturnsSummary() throws Exception {
        Path pdf = PdfTestFixtures.createPlainTextPdf(tempDir, "upload.pdf",
                "A sufficiently descriptive sentence to create a GraphRAG document.");
        ServerFixture fixture = server(true);
        try {
            HttpResponse<String> response = upload(fixture.server(), Files.readAllBytes(pdf), "application/pdf");

            assertEquals(200, response.statusCode(), response.body());
            assertEquals("ingested", JSON.parse(response.body()).get("status").getAsString().value());
                String documentUri = JSON.parse(response.body()).get("documentUri").getAsString().value();
                assertTrue(fixture.dataset().getDefaultModel().contains(
                    fixture.dataset().getDefaultModel().createResource(documentUri),
                    org.apache.jena.vocabulary.RDF.type, org.apache.jena.vocabulary.GRAG.Document));
            assertTrue(JSON.parse(response.body()).get("triplesCreated").getAsNumber().value().longValue() > 0);
        } finally {
            fixture.server().stop();
        }
    }

    @Test
    public void post_nonPdfReturnsInvalidPdfWithoutWritingRdf() throws Exception {
        assertInvalidPdf(PdfTestFixtures.createNonPdfFile(tempDir, "not-a-pdf.txt"));
    }

    @Test
    public void post_encryptedPdfReturnsInvalidPdfWithoutWritingRdf() throws Exception {
        assertInvalidPdf(PdfTestFixtures.createEncryptedPdf(tempDir, "encrypted.pdf"));
    }

    @Test
    public void post_textlessPdfReturnsInvalidPdfWithoutWritingRdf() throws Exception {
        assertInvalidPdf(PdfTestFixtures.createEmptyPagePdf(tempDir, "empty.pdf"));
    }

    @Test
    public void post_oversizedPdfReturnsPayloadTooLargeWithoutWritingRdf() throws Exception {
        String property = org.apache.jena.graphrag.ingestion.DocumentIngestionConfig.MAX_FILE_SIZE_BYTES_PROPERTY;
        String previousValue = System.getProperty(property);
        System.setProperty(property, "100");
        try {
            ServerFixture fixture = server(true);
            try {
                HttpResponse<String> response = upload(fixture.server(), new byte[101], "application/pdf");

                assertEquals(413, response.statusCode(), response.body());
                assertEquals("file_too_large", JSON.parse(response.body()).get("error").getAsObject()
                        .get("code").getAsString().value());
                assertEquals(0L, tripleCount(fixture.dataset()));
            } finally {
                fixture.server().stop();
            }
        } finally {
            if ( previousValue == null )
                System.clearProperty(property);
            else
                System.setProperty(property, previousValue);
        }
    }

    @Test
    public void post_nonPdfMediaTypeReturnsBadRequestWithoutWritingRdf() throws Exception {
        ServerFixture fixture = server(true);
        try {
            HttpResponse<String> response = upload(fixture.server(), "%PDF-ignored".getBytes(StandardCharsets.US_ASCII),
                    "application/pdf-malformed");

            assertEquals(400, response.statusCode(), response.body());
            assertEquals("invalid_content_type", JSON.parse(response.body()).get("error").getAsObject()
                    .get("code").getAsString().value());
            assertEquals(0L, tripleCount(fixture.dataset()));
        } finally {
            fixture.server().stop();
        }
    }

    @Test
    public void disabledModuleDoesNotExposeUploadEndpoint() throws Exception {
        ServerFixture fixture = server(false);
        try {
            HttpResponse<String> response = upload(fixture.server(), "%PDF-ignored".getBytes(StandardCharsets.US_ASCII),
                    "application/pdf");

            assertEquals(404, response.statusCode());
        } finally {
            fixture.server().stop();
        }
    }

    private void assertInvalidPdf(Path pdf) throws Exception {
        ServerFixture fixture = server(true);
        try {
            HttpResponse<String> response = upload(fixture.server(), Files.readAllBytes(pdf), "application/pdf");

            assertEquals(400, response.statusCode(), response.body());
            assertEquals("invalid_pdf", JSON.parse(response.body()).get("error").getAsObject()
                    .get("code").getAsString().value());
            assertEquals(0L, tripleCount(fixture.dataset()));
        } finally {
            fixture.server().stop();
        }
    }

    private static ServerFixture server(boolean enabled) {
        Model config = ModelFactory.createDefaultModel();
        if ( enabled )
            config.createResource("urn:graphrag:test")
                  .addLiteral(config.createProperty(GraphRAGModule.CONFIG_NS + "enableGraphRAG"), true);
        Dataset dataset = DatasetFactory.createTxnMem();
        FusekiServer server = FusekiServer.create()
                .port(0)
                .add("/ds", dataset)
                .parseConfig(config)
                .fusekiModules(FusekiModules.create(new GraphRAGModule()))
                .build()
                .start();
        return new ServerFixture(server, dataset);
    }

    private static HttpResponse<String> upload(FusekiServer server, byte[] pdf, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(server)))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdf))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String endpoint(FusekiServer server) {
        return "http://localhost:" + server.getPort() + "/ds/graphrag/upload";
    }

    private static long tripleCount(Dataset dataset) {
        dataset.begin(org.apache.jena.query.ReadWrite.READ);
        try {
            return dataset.getDefaultModel().size();
        } finally {
            dataset.end();
        }
    }

    private record ServerFixture(FusekiServer server, Dataset dataset) {}
}