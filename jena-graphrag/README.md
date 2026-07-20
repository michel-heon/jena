# jena-graphrag

This module provides GraphRAG ingestion, retrieval, indexing, Fuseki endpoints,
and optional provider contracts for Apache Jena.

## Scope

The module provides:

- RDF import and PDF ingestion;
- text and vector indexing;
- local, basic, and global retrieval modes;
- opt-in Fuseki endpoints;
- provider contracts for embeddings and generated content.

## Provider defaults

Provider behavior is hermetic by default:

- deterministic mock providers are used unless an operator explicitly selects
	another implementation;
- external network calls are disabled by default;
- local operation and the default test suite require no API key.

Provider implementations must not log API keys, authorization headers, or full
request and response bodies. Network providers remain optional and require
explicit configuration before they can be used.

An HTTP provider is declared through RDF with `grag:allowExternalCalls true`,
an endpoint and model, and `grag:apiKeyEnv` containing the name of an
environment variable. The secret itself must never be stored in Turtle.

The root project provides a separate real integration command:

- `make test-real-embedding`

It ingests
`docs/ref/2404.16130v2 - From Local to Global- A GraphRAG Approach to Query-Focused Summarization.pdf`,
embeds all RDF chunks and verifies vector retrieval. It exits cleanly when the
URL or key is absent and is intentionally excluded from `make verify`.

## Answer endpoint

When `grag:enableGraphRAG true` is configured, Fuseki exposes
`GET` and `POST /{dataset}/graphrag/answer?q=...&topK=...`. The operation uses
the shared hybrid retrieval service, passes retrieved chunk text to the
configured chat provider, and returns `query`, `answer`, and ordered
`citations`. Deterministic local providers remain the default.

## Build

From the Jena root directory:

- `mvn -pl :jena-graphrag -am test`

Or, as part of the full build:

- `mvn -Pdev test`
