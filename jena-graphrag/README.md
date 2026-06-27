# jena-graphrag

This module is the initial scaffolding for GraphRAG-related work in Apache Jena.

## Scope

Phase 1 provides:

- a Maven module integrated into the Jena build;
- a minimal Java package;
- a minimal vocabulary class (`GRAG`);
- minimal unit tests.

No network calls and no LLM provider dependencies are introduced here.

## Build

From the Jena root directory:

- `mvn -pl :jena-graphrag -am test`

Or, as part of the full build:

- `mvn -Pdev test`
