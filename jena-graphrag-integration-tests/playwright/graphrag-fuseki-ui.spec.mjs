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
 * Qualifies the public context contract for basic chunk, local relationship, and global community retrieval.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after every GraphRAG context mode returns its cited result shape.
 */
async function verifyGraphRAGContextModes({ page }) {
  const basic = await page.request.get(`/${dataset}/graphrag/context?q=Beta&mode=basic&topK=1`);
  expect(basic.status()).toBe(200);
  await expect(basic.json()).resolves.toEqual(expect.objectContaining({
    query: 'Beta',
    mode: 'basic',
    results: [expect.objectContaining({
      uri: 'https://jena.apache.org/graphrag/integration/corpus/chunk-beta-0',
      type: 'chunk',
      chunkUri: 'https://jena.apache.org/graphrag/integration/corpus/chunk-beta-0',
      documentUri: 'https://jena.apache.org/graphrag/integration/corpus/document-beta',
      sourceText: expect.stringContaining('Beta service'),
      chunkText: expect.stringContaining('Beta service')
    })]
  }));

  const local = await page.request.get(`/${dataset}/graphrag/context?q=Beta&mode=local&topK=1`);
  expect(local.status()).toBe(200);
  await expect(local.json()).resolves.toEqual(expect.objectContaining({
    query: 'Beta',
    mode: 'local',
    results: [expect.objectContaining({
      uri: 'https://jena.apache.org/graphrag/integration/corpus/beta-publishes-citations',
      type: 'relationship',
      entityUri: 'https://jena.apache.org/graphrag/integration/corpus/beta-service',
      entityName: 'Beta service',
      neighborUri: 'https://jena.apache.org/graphrag/integration/corpus/retrieved-chunks',
      neighborName: 'Retrieved chunks',
      sourceText: expect.stringContaining('publishes citations'),
      weight: 1
    })]
  }));

  const global = await page.request.get(`/${dataset}/graphrag/context?q=citations&mode=global&topK=1`);
  expect(global.status()).toBe(200);
  await expect(global.json()).resolves.toEqual(expect.objectContaining({
    query: 'citations',
    mode: 'global',
    results: [expect.objectContaining({
      uri: 'https://jena.apache.org/graphrag/integration/corpus/beta-citation-community',
      type: 'community',
      communityUri: 'https://jena.apache.org/graphrag/integration/corpus/beta-citation-community',
      communityTitle: 'Beta citation community',
      sourceText: expect.stringContaining('publishes citations')
    })]
  }));
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
 * Exercises every provider-free GraphRAG route exposed by the enabled Fuseki server.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after asynchronous indexing completes.
 */
async function verifyEnabledGraphRAGRoutes({ page }) {
  const configuration = await page.request.get(`/${dataset}/graphrag/config`);
  expect(configuration.status()).toBe(200);
  expect((await configuration.json()).enabled).toBe(true);

  const context = await page.request.get(`/${dataset}/graphrag/context?q=Beta&mode=local&topK=1`);
  expect(context.status()).toBe(200);
  expect(Array.isArray((await context.json()).results)).toBe(true);

  const search = await page.request.get(`/${dataset}/graphrag/search?q=Beta&topK=1`);
  expect(search.status()).toBe(200);
  expect(Array.isArray((await search.json()).results)).toBe(true);

  const indexed = await page.request.post(`/${dataset}/graphrag/index`, {
    data: {
      title: 'Enabled GraphRAG route fixture',
      content: 'The browser route fixture verifies GraphRAG indexing status.',
      sourceUri: 'urn:graphrag:browser-enabled-routes'
    }
  });
  expect(indexed.status()).toBe(202);
  const taskId = (await indexed.json()).taskId;
  expect(taskId).toEqual(expect.any(String));

  await expect.poll(async () => {
    const status = await page.request.get(`/${dataset}/graphrag/status?taskId=${encodeURIComponent(taskId)}`);
    expect(status.status()).toBe(200);
    return (await status.json()).status;
  }).toBe('done');
}

/**
 * Verifies that public invalid GraphRAG requests expose only a structured safe error.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after the browser receives the structured error response.
 */
async function verifyGraphRAGPublicError({ page }) {
  const response = await page.request.get(`/${dataset}/graphrag/answer`);
  expect(response.status()).toBe(400);
  expect(response.headers()['content-type']).toContain('application/json');

  const body = await response.json();
  expect(body.error).toEqual(expect.objectContaining({
    code: 'invalid_request',
    message: "parametre 'q' requis"
  }));
  expect(JSON.stringify(body)).not.toMatch(/api[_-]?key|authorization|bearer|secret|token/i);
}

/**
 * Verifies that Fuseki UI and SPARQL remain usable when GraphRAG is not registered.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after UI, SPARQL, and absent GraphRAG route assertions pass.
 */
async function verifyGraphRAGDisabled({ page }) {
  const ping = await page.request.get('/$/ping');
  expect(ping.status()).toBe(200);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Apache Jena Fuseki' })).toBeVisible();
  await page.getByRole('button', { name: 'query' }).click();
  const editor = page.locator('.CodeMirror textarea');
  await editor.focus();
  await editor.press('Control+A');
  await editor.pressSequentially('SELECT (1 AS ?value) WHERE {}');
  await page.getByRole('button', { name: 'Run query' }).click();
  await expect(page.locator('#yasr table.dataTable')).toContainText('1');

  const routes = [
    `/${dataset}/graphrag/config`,
    `/${dataset}/graphrag/context?q=Beta`,
    `/${dataset}/graphrag/search?q=Beta`,
    `/${dataset}/graphrag/status?taskId=absent`
  ];
  for (const route of routes) {
    expect((await page.request.get(route)).status()).toBe(404);
  }
  expect((await page.request.post(`/${dataset}/graphrag/index`, { data: {} })).status()).toBe(405);
}

/**
 * Runs SELECT queries in the delivered SPARQL Playground and asserts corpus results.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after the query result table exposes corpus values.
 */
async function verifySparqlPlaygroundQueries({ page }) {
  await page.goto(`/#/dataset/${dataset}/query`);
  const editor = page.locator('.CodeMirror textarea');
  const runQuery = page.getByRole('button', { name: 'Run query' });

  async function runSelect(query) {
    await editor.focus();
    await editor.press('Control+A');
    await editor.pressSequentially(query);
    await runQuery.click();
  }

  await runSelect(`
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    PREFIX corpus: <https://jena.apache.org/graphrag/integration/corpus/>
    SELECT ?title WHERE { corpus:document-beta grag:title ?title }
  `);
  await expect(page.locator('#yasr table.dataTable')).toContainText('Beta service brief');

  await runSelect(`
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    PREFIX corpus: <https://jena.apache.org/graphrag/integration/corpus/>
    SELECT ?chunk ?document WHERE {
      ?chunk a grag:Chunk ; grag:partOf ?document .
      FILTER(?chunk = corpus:chunk-beta-0 && ?document = corpus:document-beta)
    }
  `);
  await expect(page.locator('#yasr table.dataTable')).toContainText('chunk-beta-0');
  await expect(page.locator('#yasr table.dataTable')).toContainText('document-beta');
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

  const configuration = await page.request.get(`/${dataset}/graphrag/config`);
  expect(configuration.status()).toBe(200);
  expect(JSON.stringify(await configuration.json())).not.toMatch(/system[_-]?prompt/i);

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

/**
 * Ingests the configured GraphRAG PDF research corpus and qualifies five cited real-provider chats.
 *
 * @param {{ page: import('@playwright/test').Page }} fixtures Playwright test fixtures.
 * @returns {Promise<void>} Resolves after all PDFs are indexed and each chat response cites a PDF chunk.
 */
async function verifyUltimatePdfCorpus({ page }) {
  test.setTimeout(600_000);

  const configuration = await page.request.get(`/${dataset}/graphrag/config`);
  expect(configuration.status()).toBe(200);
  expect(JSON.stringify(await configuration.json())).not.toMatch(/system[_-]?prompt/i);

  const indexing = await page.request.post(`/${dataset}/graphrag/index`, {
    data: {
      title: 'PDF corpus vectorization trigger',
      content: 'Index the GraphRAG PDF corpus prepared by the production ingestion service.',
      sourceUri: 'urn:graphrag:browser-pdf-corpus-trigger'
    }
  });
  expect(indexing.status()).toBe(202);
  const taskId = (await indexing.json()).taskId;
  expect(taskId).toEqual(expect.any(String));

  await expect.poll(async () => {
    const task = await page.request.get(`/${dataset}/graphrag/status?taskId=${encodeURIComponent(taskId)}`);
    expect(task.status()).toBe(200);
    return (await task.json()).status;
  }, { timeout: 540_000, intervals: [1_000, 2_000, 5_000] }).toBe('done');

  const documents = await page.request.get(`/${dataset}/sparql`, {
    params: {
      query: `
        PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
        SELECT (COUNT(DISTINCT ?sourceFile) AS ?count) WHERE {
          ?document a grag:Document ; grag:sourceFile ?sourceFile .
          FILTER(STRENDS(LCASE(STR(?sourceFile)), '.pdf'))
        }
      `,
      format: 'application/sparql-results+json'
    }
  });
  expect(documents.status()).toBe(200);
  expect((await documents.json()).results.bindings).toContainEqual({
    count: { type: 'literal', value: '12', datatype: 'http://www.w3.org/2001/XMLSchema#integer' }
  });

  const questions = [
    'What is GraphRAG?',
    'What is the difference between local and global GraphRAG?',
    'How can an RDF knowledge graph support GraphRAG?',
    'What is KG2RAG?',
    'How is SPARQL generated from natural language over federated knowledge graphs?'
  ];
  for (const question of questions) {
    const answer = await page.request.get(`/${dataset}/graphrag/answer`, {
      params: { q: question, topK: '5' }
    });
    expect(answer.status()).toBe(200);
    const answerBody = await answer.json();
    expect(answerBody.answer.trim()).not.toBe('');
    expect(answerBody.citations).toEqual(expect.arrayContaining([
      expect.objectContaining({ uri: expect.stringMatching(/^http:\/\/ormynet\.com\/ns\/data#chunk-/) })
    ]));
  }
}

test('Fuseki UI exposes the GraphRAG dataset, ping, and SPARQL Playground', verifyFusekiUi);
test('Fuseki UI uploads RDF and makes the graph queryable', verifyRdfUpload);
test('Fuseki UI remains usable after indexing a bounded large document', verifyLargeDocumentIngestion);
test('Fuseki UI exposes every provider-free GraphRAG route when enabled', verifyEnabledGraphRAGRoutes);
test('Fuseki UI returns cited basic, local, and global GraphRAG context', verifyGraphRAGContextModes);
test('Fuseki UI exposes structured safe GraphRAG errors', verifyGraphRAGPublicError);
test('Fuseki UI and SPARQL remain usable without GraphRAG', verifyGraphRAGDisabled);
test('Fuseki UI Playground runs corpus and GraphRAG SELECT queries', verifySparqlPlaygroundQueries);
test('Fuseki UI real providers index and answer with a citation', verifyRealProviderAnswer);
test('Fuseki UI ultimate PDF corpus ingestion indexes and chats with real providers', verifyUltimatePdfCorpus);