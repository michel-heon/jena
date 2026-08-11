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

adr: 402
title: "Normalisation d'import GraphRAG et relations réifiées"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [400, 401]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "data"
  impact: "high"
  quality: ["maintainability", "reliability", "portability"]
  reversibility: "moderate"
  scope: "tactical"
  tech_areas: ["rdf", "sparql", "vocabulary", "graphrag", "java"]
tags: ["rdf", "mg", "import", "normalisation", "relationship"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 402 : Normalisation d'import GraphRAG et relations réifiées

## Contexte vérifié

Les dumps GraphRAG peuvent employer des termes `snake_case` et représenter une
relation Entity vers Entity par une singleton property. Cette forme ne fournit ni
les termes `camelCase` utilisés par le vocabulaire `GRAG`, ni un nœud relation
directement citable. `GraphRAGImporter` est le chemin de production qui traite
ces deux écarts.

## Décision

L'importeur préserve les URI source et normalise les termes `mg:` connus vers les
termes `camelCase` du vocabulaire de production. Lorsqu'un prédicat singleton est
typé `mg:related_to`, l'import produit :

- un nœud `mg:Relationship` portant `mg:source`, `mg:target` et les métadonnées
  disponibles, notamment `mg:rank`, `mg:weight` et `mg:description` ;
- l'arête de commodité `mg:relatedTo` entre les deux entités ;
- les prédicats hors namespace `mg:` sans les supprimer ni les renommer.

L'opération est idempotente selon la sémantique ensembliste RDF : une seconde
importation du même graphe ne fabrique ni URI ni relation supplémentaire.

## Conséquences

Les requêtes GraphRAG peuvent parcourir l'arête directe tandis que les réponses
peuvent citer une relation portant sa preuve. Le doublon volontaire entre le nœud
réifié et l'arête directe est le coût de ces deux usages. Tout nouveau mapping de
vocabulaire ou changement de namespace devra faire évoluer cette décision.

## Références

- [ADR-400, vocabulaire et assertions](./400-DATA-vocabulaire-rdf-grag.md)
- [GraphRAGImporter](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/GraphRAGImporter.java)