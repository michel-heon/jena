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

## Real provider prerequisites

Real-provider scenarios must define the environment-variable names in their GraphRAG assembler configuration with `grag:apiKeyEnv` and, when applicable, `grag:endpointEnv`. `ExternalProviderPrerequisites.requireConfiguredEnvironment(provider, System.getenv())` reports every configured variable that is absent or blank before a network call. It reports variable names only and never logs a secret value.

## Validation

```bash
mvn -Pgraphrag -pl jena-graphrag-integration-tests -Dtest=TestCorpusManifest,TestExternalProviderPrerequisites test
```

To execute the tranche-2 ingestion and local retrieval checks:

```bash
mvn -Pgraphrag -pl jena-graphrag-integration-tests -Dtest=TestGraphRAGIngestionIntegration,TestCorpusManifest test
```