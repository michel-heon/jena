<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements. See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership. The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied. See the License for the
   specific language governing permissions and limitations
   under the License.

   SPDX-License-Identifier: Apache-2.0
-->

# Apache Jena GraphRAG Integration Tests

This module qualifies GraphRAG behavior that crosses module or process boundaries. It is included by the root `graphrag` Maven profile after `jena-graphrag`.

## Tranche 1

The initial tranche provides the Maven module, authored RDF fixtures and PDF reference fixtures, their versioned provenance manifest, and executable checks for corpus completeness, SHA-256 integrity, RDF validity, PDF signatures, and malformed input rejection.

The corpus is organised as follows:

- `corpus/ingestion/`: RDF input for ingestion and import scenarios.
- `corpus/chat/`: RDF input whose documents and chunks can later be used as citation sources.
- `corpus/invalid/`: deliberately malformed input used to qualify rejection paths.

`corpus/manifest.properties` records a stable identifier, relative path, provenance, licence, SHA-256, expected facts, scenarios, and ingestion path for every fixture.

The PDF reference fixtures were copied from the user-provided `/home/michel/00-GIT/jena-graphrag-project/docs/ref/` directory on request. Their manifest entries state `unverified-by-request` for their licence; they must not be treated as a redistribution clearance.

## Tranche 2

The integration suite invokes production GraphRAG services against the versioned corpus. It verifies that RDF import preserves source identifiers, normalizes GraphRAG `snake_case` terms, reifies singleton relationship predicates, retains foreign predicates, remains idempotent, and rejects malformed input without committing data.

It also ingests a real PDF fixture through `DocumentIngestionService`, checks the emitted document and chunk provenance, then indexes those chunks in a real in-memory Lucene text index. `GraphRAGContextService` retrieves the indexed chunk by a term extracted from its ingested text. This local retrieval path makes no external provider call and uses no mocked provider.

## Tranche 3

`TestGraphRAGFusekiContracts` starts real ephemeral Fuseki servers with the production `GraphRAGModule`, imported RDF corpus and `/$/ping` enabled. It qualifies the enabled module's JSON configuration and context contracts, structured request errors for `search`, `answer` and `index`, and an unknown indexing-task response. It also proves that disabling GraphRAG leaves ping available while every tested GraphRAG route returns `404`.

The suite deliberately exercises only paths that complete before a chat or embedding provider is called. Provider-backed search, indexing and answers require real configured credentials and remain tranche-4 scenarios.

## Real provider prerequisites

Real-provider scenarios must define the environment-variable names in their GraphRAG assembler configuration with `grag:apiKeyEnv` and, when applicable, `grag:endpointEnv`. `ExternalProviderPrerequisites.requireConfiguredEnvironment(provider, System.getenv())` reports every configured variable that is absent or blank before a network call. It reports variable names only and never logs a secret value.

## Tranche 4: real providers

The opt-in `graphrag-real-providers` profile runs `RealProviderGraphRAGIT`. It starts an ephemeral production Fuseki server configured with the production HTTP embedding and chat providers, indexes a dedicated corpus, then verifies retrieval, a non-empty answer, and a citation to that corpus. It never uses a mock provider, a fake HTTP server, or a JUnit skip.

Prepare a local provider profile with:

```bash
make -C jena-graphrag-integration-tests bootstrap-real-providers
```

This idempotent command checks Git, Java and Maven, creates `env/.env.user` from its non-secret example when absent, and verifies that the local file is ignored by Git. It reads local values only to generate ignored `0600` projections for Make, shell scripts, Maven and Java; it never requests provider values or writes them to output.

Set these environment variables in the invoking shell. The endpoint and API-key variables are referenced through the assembler configuration; their values are not written to test output.

| Variable | Purpose |
|----------|---------|
| `GRAPHRAG_EMBEDDING_API_URL` | OpenAI-compatible embeddings endpoint |
| `GRAPHRAG_EMBEDDING_API_KEY` | Embeddings provider API key |
| `GRAPHRAG_EMBEDDING_MODEL` | Embeddings model identifier |
| `GRAPHRAG_EMBEDDING_DIMENSION` | Dimension emitted by the embeddings model |
| `OPENAI_API_URL` | OpenAI-compatible chat endpoint |
| `OPENAI_API_KEY` | Chat provider API key |
| `GRAPHRAG_CHAT_MODEL` | Chat model identifier |

For Azure legacy endpoints matching `/openai/deployments/{deployment-id}/...`, the bootstrap derives blank model variables from `{deployment-id}` and writes the OpenAI-compatible `/openai/v1/` base URL to the generated projections. This follows the Microsoft Foundry requirement that `model` contains the deployment name. Lucene 10.3.1 accepts at most `1024` dimensions, so the bootstrap derives `1024` for an unrenamed standard embedding deployment and the HTTP provider sends that value in the embedding request. A custom deployment name requires an explicit `GRAPHRAG_EMBEDDING_DIMENSION`, because the endpoint does not reveal its backing model or any reduced `dimensions` setting.

Provider calls have a 60-second request timeout. Chat context is bounded by the production 4096-token input budget; no answer wording is asserted. A missing or blank variable fails before Fuseki starts and identifies only the missing variable names.

Provider failures returned by `/{dataset}/graphrag/answer` are structured and do not expose endpoint, credential, or provider response details: an authentication rejection is `502` with `provider_authentication_failed`, a provider timeout is `504` with `provider_timeout`, and other provider failures are `502` with `provider_unavailable`.

## Root Make targets

From the repository root, the Make facade orchestrates the completed integration tranches:

```bash
make graphrag-integration-ingestion
make graphrag-integration-api
make graphrag-integration-chat
make graphrag-integration-real-providers-smoke
make graphrag-integration
```

The first target runs corpus, provider-prerequisite, ingestion, indexing, and retrieval checks. The API target runs the real Fuseki contracts and the GraphRAG answer endpoint contract. The chat target bootstraps the local real-provider environment and runs `RealProviderGraphRAGIT`. The smoke target runs `npm ci`, installs Chromium through the locked Playwright version, starts `GraphRAGFusekiUIServer` on an ephemeral port, and checks the delivered Fuseki UI, `/$/ping`, the preloaded dataset, the GraphRAG configuration and context endpoints, and the SPARQL Playground. The real-provider smoke target uses the generated local environment to configure an ephemeral index in the UI server, indexes a document from the browser request context, and verifies a non-empty answer with a citation. It never prints provider values.

The smoke runner stops Fuseki after every result. Playwright traces, screenshots, videos, reports, and the Fuseki log are retained under `target/playwright/` only when the smoke suite fails. The aggregate runs all four targets and therefore requires the real-provider configuration.

## Validation

```bash
mvn -Pgraphrag -pl jena-graphrag-integration-tests -Dtest=TestCorpusManifest,TestExternalProviderPrerequisites test
```

To execute the tranche-2 ingestion and local retrieval checks:

```bash
mvn -Pgraphrag -pl jena-graphrag-integration-tests -Dtest=TestGraphRAGIngestionIntegration,TestCorpusManifest test
```

To execute the tranche-3 real Fuseki API contracts:

```bash
mvn -Pgraphrag -pl jena-graphrag-integration-tests -Dtest=TestGraphRAGFusekiContracts test
```

To execute the tranche-4 real-provider qualification:

```bash
set -a
. jena-graphrag-integration-tests/env/.env.user
set +a
mvn -Pgraphrag,graphrag-real-providers -pl jena-graphrag-integration-tests test
```
