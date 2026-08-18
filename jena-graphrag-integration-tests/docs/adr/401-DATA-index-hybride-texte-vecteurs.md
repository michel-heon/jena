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

adr: 401
title: "Index GraphRAG texte et vecteurs Lucene"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [400, 402, 403]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "data"
  impact: "high"
  quality: ["performance", "maintainability", "portability", "reliability"]
  reversibility: "hard"
  scope: "tactical"
  tech_areas: ["jena-text", "lucene", "vector-search", "embeddings", "java"]
tags: ["lucene", "text-index", "vector", "knn", "embeddings", "graphrag"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "high"
---

# ADR 401 : Index GraphRAG texte et vecteurs Lucene

## Contexte vérifié

`jena-graphrag` maintient deux index Lucene distincts : l'index textuel, créé par
`GraphRAGTextDatasetFactory` au-dessus de `jena-text`, et l'index vectoriel,
encapsulé par `LuceneVectorIndex`. Les deux index sont ouverts par `GraphRAGIndex`
et alimentés pendant l'indexation GraphRAG ; aucune base vectorielle externe n'est
nécessaire pour le scénario de qualification.

Lucene 10.3.1 est la version gérée par le parent Maven. Le contrat local de
`LuceneVectorIndex` borne les vecteurs à $1 \ldots 1024$ dimensions. Cette borne
est vérifiée avant l'ouverture de l'index et la dimension effective de chaque
vecteur est vérifiée à l'indexation et à la recherche.

Microsoft Foundry permet de demander une dimension réduite pour les modèles
`text-embedding-3`. Le fournisseur HTTP demande $1024$ dimensions, compatible
avec Lucene. Les sorties par défaut à $1536$ ou $3072$ ne sont pas compatibles
avec cet index sans réduction explicite côté fournisseur ou sans une autre
implémentation de `VectorIndex`.

## Décision

- Réutiliser `jena-text` pour le texte et Lucene KNN (`KnnFloatVectorField` et
  `KnnFloatVectorQuery`) pour les vecteurs, dans des répertoires séparés.
- Conserver `VectorIndex` comme frontière d'abstraction afin de permettre une
  autre implémentation si la dimension ou l'exploitation l'exige.
- Exiger une dimension identique dans l'assembleur, les vecteurs d'indexation et
  les vecteurs de requête ; tout écart échoue explicitement.
- Accepter les fournisseurs réels uniquement lorsque leur dimension configurée
  est dans la plage Lucene. La qualification Azure couvre le cas $1024$.

## Conséquences

Cette décision fournit une recherche locale sans service vectoriel additionnel et
rend les incompatibilités de dimension déterministes au démarrage ou à l'appel du
fournisseur. Elle ne promet pas la conservation de toute dimension native : une
évolution au-delà de $1024$ devra soit demander une réduction du fournisseur, soit
introduire une implémentation `VectorIndex` distincte par ADR.

## Références

- [ADR-400, vocabulaire et assertions](./400-DATA-vocabulaire-rdf-grag.md)
- [LuceneVectorIndex](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/index/LuceneVectorIndex.java)
- [TestLuceneVectorIndex](../../../jena-graphrag/src/test/java/org/apache/jena/graphrag/index/TestLuceneVectorIndex.java)
- [Lucene 10.3.1, KnnFloatVectorField : constructeurs limités à 1024 dimensions](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/document/KnnFloatVectorField.html#constructor-detail)
- [Lucene 10.3.1, KnnFloatVectorField.createFieldType : dimension supérieure à 1024 rejetée](https://lucene.apache.org/core/10_3_1/core/org/apache/lucene/document/KnnFloatVectorField.html#createFieldType(int,org.apache.lucene.index.VectorSimilarityFunction))
- [Documentation Microsoft Foundry, embeddings](https://learn.microsoft.com/azure/ai-foundry/openai/how-to/embeddings)