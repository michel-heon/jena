/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { expect, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const dataset = 'graphrag-smoke';
const rdfUploadFixture = fileURLToPath(new URL('../src/test/resources/corpus/ingestion/team-graph.ttl', import.meta.url));
const rdfUploadGraph = 'https://jena.apache.org/graphrag/integration/ui-upload';
const largeDocumentFixture = fileURLToPath(new URL('../src/test/resources/corpus/ingestion/bounded-large-document.txt', import.meta.url));

/**
 * Verifies the browser-visible Fuseki UI and provider-free GraphRAG endpoints.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after the UI and endpoint assertions pass.
 */
async function verifyFusekiUi({ page }) {
  const ping = await page.request.get('/$/ping');
  expect(ping.status()).toBe(200);

  const configuration = await page.request.get(`/${dataset}/graphrag/config`);
  expect(configuration.status()).toBe(200);
  expect((await configuration.json()).enabled).toBe(true);

  const context = await page.request.get(`/${dataset}/graphrag/context?q=Beta&mode=local&topK=1`);
  expect(context.status()).toBe(200);
  expect(Array.isArray((await context.json()).results)).toBe(true);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Apache Jena Fuseki' })).toBeVisible();
  await expect(page.getByText(dataset, { exact: false })).toBeVisible();

  await page.getByRole('button', { name: 'query' }).click();
  await expect(page).toHaveURL(new RegExp(`/dataset/${dataset}/query`));
  await expect(page.getByText('To try out some SPARQL queries against the selected dataset, enter your query here.')).toBeVisible();
}

/**
 * Uploads a versioned RDF fixture through the delivered Fuseki UI and verifies its data.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after Graph Store upload and SPARQL verification succeed.
 */
async function verifyRdfUpload({ page }) {
  await page.goto(`/#/dataset/${dataset}/upload`);
  await expect(page.getByRole('heading', { name: `/${dataset}`, exact: true })).toBeVisible();

  await page.getByRole('textbox', { name: 'Dataset graph name' }).fill(rdfUploadGraph);
  await page.locator('input[type="file"]').setInputFiles(rdfUploadFixture);
  const upload = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes(`/${dataset}/data`) && response.ok()
  );
  await page.getByRole('button', { name: 'upload all' }).click();
  await upload;
  await expect(page.getByText('1/1', { exact: true })).toBeVisible();

  const query = `
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    PREFIX corpus: <https://jena.apache.org/graphrag/integration/corpus/>
    SELECT ?name WHERE { GRAPH <${rdfUploadGraph}> { corpus:team-alpha grag:name ?name } }
  `;
  const result = await page.request.get(`/${dataset}/sparql`, {
    params: { query, format: 'application/sparql-results+json' }
  });
  expect(result.status()).toBe(200);
  expect((await result.json()).results.bindings).toContainEqual({
    name: { type: 'literal', value: 'Alpha team' }
  });
}

/**
 * Indexes a bounded large document and verifies that Fuseki remains browser-accessible.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after indexing, ping, and Playground checks succeed.
 */
async function verifyLargeDocumentIngestion({ page }) {
  const content = await readFile(largeDocumentFixture, 'utf8');
  expect(content.length).toBeGreaterThanOrEqual(10_000);
  expect(content.length).toBeLessThanOrEqual(20_000);

  const indexed = await page.request.post(`/${dataset}/graphrag/index`, {
    data: {
      title: 'Bounded large browser indexing fixture',
      content,
      sourceUri: 'urn:graphrag:browser-bounded-large-document'
    }
  });
  expect(indexed.status()).toBe(202);
  const taskId = (await indexed.json()).taskId;
  expect(taskId).toEqual(expect.any(String));

  await expect.poll(async () => {
    const task = await page.request.get(`/${dataset}/graphrag/status?taskId=${encodeURIComponent(taskId)}`);
    expect(task.status()).toBe(200);
    return (await task.json()).status;
  }).toBe('done');

  const ping = await page.request.get('/$/ping');
  expect(ping.status()).toBe(200);
  await page.goto(`/#/dataset/${dataset}/query`);
  await expect(page.getByRole('heading', { name: `/${dataset}`, exact: true })).toBeVisible();
  await expect(page.getByText('To try out some SPARQL queries against the selected dataset, enter your query here.')).toBeVisible();
}

/**
 * Indexes a document through the public GraphRAG API and verifies a real-provider answer.
 *
 * The launcher supplies the provider configuration through its process environment; no
 * provider values are inspected or emitted by this test.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after indexing completes and the answer cites the document.
 */
async function verifyRealProviderAnswer({ page }) {
  test.setTimeout(150_000);

  const indexed = await page.request.post(`/${dataset}/graphrag/index`, {
    data: {
      title: 'GraphRAG browser integration corpus',
      content: 'Apache Jena GraphRAG indexes cited knowledge from browser smoke tests.',
      sourceUri: 'urn:graphrag:browser-real-provider'
    }
  });
  expect(indexed.status()).toBe(202);
  const taskId = (await indexed.json()).taskId;
  expect(taskId).toEqual(expect.any(String));

  // Indexing is asynchronous; poll the public task resource until it reports completion.
  await expect.poll(async () => {
    const task = await page.request.get(`/${dataset}/graphrag/status?taskId=${encodeURIComponent(taskId)}`);
    expect(task.status()).toBe(200);
    return (await task.json()).status;
  }, { timeout: 120_000 }).toBe('done');

  const answer = await page.request.get(
    `/${dataset}/graphrag/answer?q=${encodeURIComponent('What does Apache Jena GraphRAG index?')}`
  );
  expect(answer.status()).toBe(200);
  const answerBody = await answer.json();
  expect(answerBody.answer.trim()).not.toBe('');
  expect(answerBody.citations).toEqual(expect.arrayContaining([
    expect.objectContaining({ uri: expect.stringMatching(/^urn:graphrag:browser-real-provider#chunk-/) })
  ]));
}

test('Fuseki UI exposes the GraphRAG dataset, ping, and SPARQL Playground', verifyFusekiUi);
test('Fuseki UI uploads RDF and makes the graph queryable', verifyRdfUpload);
test('Fuseki UI remains usable after indexing a bounded large document', verifyLargeDocumentIngestion);
test('Fuseki UI real providers index and answer with a citation', verifyRealProviderAnswer);