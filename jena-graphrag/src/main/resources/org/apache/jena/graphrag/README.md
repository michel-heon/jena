# GraphRAG ontology (msft-graphrag)

This resource package contains the GraphRAG RDF ontology used by the Jena GraphRAG module.

## Provenance

The ontology file [grag.ttl](grag.ttl) is the Microsoft GraphRAG RDF ontology published by Ian Ormesher.

- Upstream namespace: `http://ormynet.com/ns/msft-graphrag#`
- Upstream prefix: `mg:`
- Blog series: "Microsoft GraphRAG with an RDF Knowledge Graph" (Parts 1-3, by Ian Ormesher on Medium)
- Companion repository: https://github.com/ianormy/msft_graphrag_blog (folder `data/`)

This file vendors the upstream ontology as-is. The namespace and prefix are intentionally kept identical to the upstream so that RDF data produced by tools targeting the Microsoft GraphRAG ontology (and SPARQL queries written against `mg:`) remain interoperable with this module.

## Adaptations

The only changes applied to the upstream file are documentation and licensing metadata:

- ASF license header added at the top for Apache RAT compliance.
- Bilingual `rdfs:label` and `rdfs:comment` annotations (`@en` / `@fr`) added on classes and properties to ground each term in the GraphRAG paper (arXiv:2404.16130v2) and to flag implementation-derived fields explicitly.
- A `dct:source <https://arxiv.org/abs/2404.16130>` triple was added on the ontology resource.
- File serialised in Turtle with SPARQL-style `PREFIX` declarations (TriG-compatible).

No class, property, IRI or term name was changed. The ontology IRI itself remains `<http://ormynet.com/ns/msft-graphrag>` and all terms remain under `<http://ormynet.com/ns/msft-graphrag#>`.

## Java vocabulary

The Java mirror of this ontology is `org.apache.jena.vocabulary.GRAG` (class `GRAG`). Its `GRAG.uri` constant equals `http://ormynet.com/ns/msft-graphrag#` and resources/properties match the local names defined in this file.

## Fuseki assembler vocabulary

The Fuseki configuration vocabulary is separate from the Microsoft GraphRAG data
vocabulary. It uses the `grag:` prefix under
`https://jena.apache.org/graphrag/vocab#` and is mirrored by
`org.apache.jena.graphrag.index.GraphRAGAssemblerVocab`.

`GraphRAGIndexAssembler` opens a `grag:GraphRAGIndex` only when the loaded
configuration model also contains `grag:enableGraphRAG true`. It reads
`grag:textIndexDir`, `grag:vectorIndexDir` and optional `grag:vectorDimension`,
then creates the Lucene text index and Lucene KNN vector index on disk.

An executable Turtle example is available in
`src/test/resources/org/apache/jena/graphrag/graphrag-index-assembler.ttl`.

## Validation

The file is parseable by Apache Jena's `riot` tool. Because it uses SPARQL-style `PREFIX` declarations rather than Turtle's `@prefix`, validate it with the TriG syntax:

```bash
riot --syntax=TriG --validate grag.ttl
```
