---
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0

adr: 403
title: "Ingestion PDF, chunking et traçabilité GraphRAG"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [400, 401, 402]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "data"
  impact: "high"
  quality: ["reliability", "security", "maintainability", "compliance"]
  reversibility: "moderate"
  scope: "tactical"
  tech_areas: ["pdfbox", "rdf", "ingestion", "chunking", "java"]
tags: ["pdf", "chunking", "ingestion", "traceability", "mg-vocabulary"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 403 : Ingestion PDF, chunking et traçabilité GraphRAG

## Contexte vérifié

`DocumentIngestionService` ingère localement un PDF dans un `Dataset` Jena. Il
utilise PDFBox 3.x, sans OCR ni accès réseau, puis crée des ressources
`mg:Document` et `mg:Chunk` compatibles avec le contrat de l'ADR-400.

## Décision

- Valider le fichier avant toute écriture : fichier régulier et lisible, taille
  maximale configurée, signature `%PDF-`, PDF non chiffré et texte extractible.
- Extraire le texte avec PDFBox 3.x, le normaliser et le découper de façon
  déterministe par `TextChunker`, avec taille et chevauchement configurables.
- Utiliser une transaction `Dataset` : aucune donnée partielle n'est visible si
  l'extraction, le découpage ou l'écriture échoue.
- Dériver les URI de document et de chunk du SHA-256 du contenu et de l'index du
  chunk, puis conserver `mg:sourceHash`, `mg:sourceFile`, `mg:chunkIndex` et,
  lorsque disponible, `mg:chunkPages`.

## Conséquences

Une même entrée produit des URI et un graphe reproductibles ; l'ingestion est
idempotente et les chunks restent reliés à leur source pour la recherche et les
citations. Le service est volontairement limité aux PDF textuels locaux. OCR,
documents chiffrés et sources réseau exigeraient une décision de sécurité dédiée.

## Références

- [ADR-400, vocabulaire et assertions](./400-DATA-vocabulaire-rdf-grag.md)
- [DocumentIngestionService](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/ingestion/DocumentIngestionService.java)
- [PdfTextExtractor](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/ingestion/PdfTextExtractor.java)