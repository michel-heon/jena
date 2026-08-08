---
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
# SPDX-License-Identifier: Apache-2.0

adr: 101
title: "Qualification des fournisseurs réels derrière les SPI GraphRAG"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [2, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "architecture"
  impact: "high"
  quality:
    - "reliability"
    - "portability"
    - "security"
  reversibility: "moderate"
  scope: "tactical"
  tech_areas:
    - "jena-graphrag"
    - "langchain4j"
    - "llm"
    - "embeddings"
    - "real-provider"
    - "integration-testing"
tags: ["langchain4j", "provider", "embeddings", "chat", "no-mock"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 101 : Qualification des fournisseurs réels derrière les SPI GraphRAG

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut** | Accepté |
| **Décision héritée** | LangChain4j reste un détail d'implémentation derrière les SPI de `jena-graphrag` |
| **Décision locale** | La suite externe qualifie ces SPI avec de vrais fournisseurs, sans mock |
| **Issue** | [#2](https://github.com/michel-heon/jena/issues/2) |

## Contexte vérifié

Le module [`jena-graphrag`](../../../jena-graphrag/) expose ses propres contrats de fournisseurs et conserve LangChain4j derrière ses adaptateurs :

- `ChatCompletionProvider` pour le chat ;
- `EmbeddingProvider` pour les vecteurs ;
- `HttpChatCompletionProvider` et `HttpEmbeddingProvider` pour les appels externes ;
- `ProviderConfiguration` pour l'autorisation explicite, le délai et le budget de tokens.

Le [`pom.xml` du module](../../../jena-graphrag/pom.xml) importe le BOM LangChain4j 1.8.0 et `langchain4j-open-ai`. Le chemin OpenAI-compatible utilise `OpenAiChatModel` et `OpenAiEmbeddingModel`. Le chemin Azure détecté utilise l'adaptateur HTTP Azure local. Dans tous les cas, `allowExternalCalls` doit être vrai, les retries LangChain4j sont désactivés et la journalisation des requêtes/réponses est désactivée.

Le code de production propose également des implémentations `Mock*` pour son fonctionnement hermétique. L'issue #2 interdit explicitement leur emploi dans ce module de qualification et interdit tout fallback implicite vers ces implémentations.

## Décision

Les tests d'intégration construisent et exercent les fournisseurs réels à travers les SPI et mécanismes de configuration de production de `jena-graphrag`. Ils ne dépendent pas directement des types LangChain4j dans leurs assertions métier.

### Règles

1. Aucun test de ce module n'instancie ou ne référence une classe `Mock*`.
2. Aucun faux serveur HTTP ni réponse de fournisseur préfabriquée n'est utilisé.
3. Les endpoints, modèles, dimensions, délais, budgets et noms de variables d'environnement sont documentés dans le README du module au moment de leur implémentation.
4. Les valeurs secrètes sont lues uniquement depuis l'environnement et ne sont jamais affichées.
5. Une configuration obligatoire absente provoque un échec rapide pour la cible de chat ; elle ne produit ni test ignoré par une assumption JUnit, ni succès partiel, ni fallback.
6. Le test d'embeddings vérifie la dimension, l'indexation du corpus et la récupération d'une source attendue.
7. Le test de chat vérifie une réponse non vide et au moins une citation vers le document attendu ; il ne compare pas une phrase exacte.
8. Les erreurs de délai, d'authentification et de fournisseur sont vérifiées par leur catégorie observable sans exposer la requête, la réponse complète ou le secret.
9. Un appel réussi à `/graphrag/answer` n'est qualifié que si l'index et les fournisseurs réels sont explicitement configurés. Le fallback `MockChatCompletionProvider` utilisé par la configuration hermétique de production ne peut pas satisfaire ce scénario.

## Pourquoi tester les SPI

| Option | Décision |
|--------|----------|
| Appeler directement LangChain4j depuis les tests | Rejetée : couple la suite externe à un détail d'implémentation |
| Tester les SPI avec un mock | Rejetée : ne qualifie ni le réseau, ni l'authentification, ni le modèle réel |
| Tester les adaptateurs de production derrière les SPI | Retenue : qualifie le livrable tout en préservant la liberté d'implémentation |

## Conséquences

### Positives

- La suite détecte les incompatibilités réelles de fournisseur, de modèle et de dimension.
- Une évolution interne de LangChain4j reste possible tant que les SPI et comportements observables sont préservés.
- Les assertions restent robustes face au caractère non déterministe du texte généré.
- La politique de secrets devient testable dans les sorties et artefacts d'échec.

### Négatives

- Les scénarios chat et embeddings exigent une infrastructure et peuvent avoir un coût.
- Leur disponibilité et leur latence dépendent d'un service réel.
- Les prérequis doivent être diagnostiqués avant de démarrer la suite.

## Validation attendue

| Cible de l'issue #2 | Résultat attendu |
|----------------------|-----------------|
| `make graphrag-integration-chat` avec configuration complète | Embeddings et réponse réels, citation attendue, aucun secret exposé |
| `make graphrag-integration-chat` sans prérequis | Échec rapide, diagnostic explicite, aucun fallback |
| Recherche vectorielle | Dimension attendue et document du corpus retrouvé |
| Réponse GraphRAG | Réponse non vide et citation issue du corpus |

## Références

- [ADR-002, Usage vérifié des agents IA](./002-META-agent-ia-non-hallucination.md)
- [ADR-608, Non-duplication fonctionnelle transversale](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)
- [`HttpChatCompletionProvider`](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/provider/HttpChatCompletionProvider.java)
- [`HttpEmbeddingProvider`](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/provider/HttpEmbeddingProvider.java)
- [`ProviderConfiguration`](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/provider/ProviderConfiguration.java)
- [LangChain4j](https://docs.langchain4j.dev/)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation à la qualification externe | Transformer la décision d'implémentation en contrat de test réel pour l'issue #2 |
