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

adr: 602
title: "Makefile racine comme orchestrateur des tests d'intégration GraphRAG"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [601, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "devops"
  impact: "medium"
  quality:
    - "maintainability"
    - "reliability"
    - "usability"
    - "security"
  reversibility: "moderate"
  scope: "tactical"
  tech_areas:
    - "makefile"
    - "maven"
    - "playwright"
    - "process-lifecycle"
    - "integration-testing"
tags: ["make", "orchestration", "maven", "playwright", "fuseki"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 602 : Makefile racine comme orchestrateur des tests d'intégration GraphRAG

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut de la décision** | Accepté |
| **Statut d'implémentation au 2026-08-08** | À réaliser dans l'issue #2 |
| **Portée** | Façade commune pour Maven, Fuseki, fournisseurs et Playwright |

## Contexte

L'[issue #2](https://github.com/michel-heon/jena/issues/2) exige des commandes reproductibles depuis la racine pour construire, démarrer, attendre, tester et arrêter la qualification GraphRAG. Les scénarios utilisent plusieurs outils avec des cycles de vie différents :

- Maven compile le réacteur et exécute les tests JUnit ;
- un vrai processus Fuseki doit être démarré sur un port disponible, attendu puis arrêté ;
- les tests de fournisseurs réels doivent valider leurs prérequis ;
- Playwright installe Chromium et exécute les smoke tests black-box ;
- les traces et journaux doivent être conservés en cas d'échec puis expurgés de tout secret.

Au 2026-08-08, aucun Makefile racine n'existe dans ce dépôt. Cette décision décrit donc la cible à implémenter et ne présente aucune commande comme déjà disponible.

## Décision

Le Makefile racine devient la façade publique de la qualification `jena-graphrag-integration-tests`. Il orchestre les outils existants mais ne réimplémente ni le build Maven, ni les assertions JUnit, ni les scénarios Playwright.

### Cibles publiques

| Cible | Responsabilité |
|-------|----------------|
| `graphrag-integration-ingestion` | Compiler puis exécuter les scénarios d'ingestion, d'indexation et de récupération |
| `graphrag-integration-api` | Démarrer Fuseki réel, attendre sa santé, tester les contrats ne nécessitant pas de génération réelle et arrêter le processus |
| `graphrag-integration-chat` | Valider les prérequis puis exécuter embeddings et chat avec de vrais fournisseurs |
| `graphrag-integration-smoke` | Démarrer le livrable et exécuter les parcours Playwright |
| `graphrag-integration` | Enchaîner les suites applicables dans l'environnement documenté |

Les cibles de cycle de vie peuvent être exposées séparément si les contributeurs en ont besoin, mais restent sous le préfixe défini par ADR-601.

### Responsabilités par outil

| Surface | Responsabilité |
|---------|----------------|
| Make | Dépendances, ordre, validation légère des outils, façade d'aide et propagation du code de sortie |
| Maven | Réacteur, compilation, dépendances Java, sélection et exécution JUnit |
| JUnit | Ingestion, invariants RDF, indexation, contrats Java et API difficiles à observer depuis un navigateur |
| Playwright | Démarrage observable, `/$/ping`, UI livrée et parcours question/réponse cité |
| Scripts dédiés | Cycle de vie de processus, attente, collecte ou expurgation lorsque la recette dépasse une commande lisible |

### Règles de cycle de vie

1. Fuseki utilise un port disponible ou explicitement fourni ; aucune valeur globale n'est supposée libre.
2. Le PID et le répertoire temporaire appartiennent à une exécution identifiée.
3. L'arrêt est enregistré avant le lancement des assertions et s'exécute après succès, échec ou interruption.
4. Une cible API ou smoke réussie ne laisse aucun processus serveur actif.
5. Les journaux et traces sont collectés lors d'un échec sans masquer le code de sortie initial.
6. Les artefacts sont expurgés avant publication ; aucune clé ou en-tête d'autorisation n'est affiché.

### Prérequis des fournisseurs

La cible de chat valide les noms de variables obligatoires, leur présence et les valeurs non secrètes comme le modèle ou la dimension. Elle n'affiche jamais la valeur d'une clé. Une absence provoque un échec explicite avant le démarrage d'une suite coûteuse et ne déclenche aucun fallback vers un provider `Mock*`.

### Frontière entre API et chat

La cible API vérifie `/$/ping`, la configuration, le contexte, la recherche, les statuts, les types de contenu, les erreurs structurées et la désactivation. Pour `/graphrag/answer`, elle peut vérifier les erreurs qui précèdent tout appel fournisseur, par exemple une requête invalide ou un module désactivé.

Le scénario positif de `/graphrag/answer` appartient à la cible chat : il exige un index, un fournisseur d'embeddings et un fournisseur de chat réels explicitement configurés. Il ne doit jamais réussir grâce au `MockChatCompletionProvider` de repli utilisé par la configuration hermétique de `GraphRAGModule` lorsqu'aucun index n'est configuré.

### Reproductibilité Playwright

- les dépendances Node sont installées depuis le lockfile avec la commande reproductible du gestionnaire retenu ;
- la version de Playwright et le navigateur sont pilotés par le module ;
- les traces, captures et vidéos suivent la politique « uniquement à l'échec » définie dans la configuration ;
- la cible ne crée pas une UI GraphRAG inexistante et ne teste que l'UI effectivement livrée.

## Alternatives

### Documentation de commandes brutes

Rejetée : elle ne garantit ni le nettoyage des processus, ni l'ordre, ni une interface stable.

### Tout implémenter dans Make

Rejetée : les recettes complexes de cycle de vie et d'expurgation seraient difficiles à tester et à maintenir.

### Un script monolithique

Rejetée : il mélangerait build, serveurs, fournisseurs et navigateurs, avec un diagnostic et un nettoyage fragiles.

### Ajouter un nouvel orchestrateur externe

Rejetée pour cette livraison : l'issue demande un Makefile et les outils requis sont déjà Maven, Node et Playwright.

## Conséquences

### Positives

- Une entrée stable permet d'exécuter une tranche ou toute la qualification.
- Les développeurs et la CI utilisent les mêmes commandes.
- Le nettoyage et la collecte des preuves deviennent des contrats explicites.
- Maven et Playwright restent responsables de leurs domaines respectifs.

### Négatives

- Le Makefile et les éventuels scripts forment une surface supplémentaire à tester.
- Les scénarios externes exigent un environnement documenté.
- L'agrégateur doit distinguer clairement les suites toujours exécutables de celles qui nécessitent des credentials.

## Critères de succès

- toutes les cibles annoncées par l'issue #2 existent et sont documentées ;
- `graphrag-integration-api` et `graphrag-integration-smoke` ne laissent aucun Fuseki actif ;
- `graphrag-integration-chat` échoue rapidement si sa configuration manque ;
- les commandes ciblées conservent le code de sortie du test qui échoue ;
- les logs et traces utiles sont disponibles à l'échec sans secret ;
- `graphrag-integration` agrège les suites selon la politique documentée.

## Références

- [ADR-601, Nomenclature des cibles et scripts](./601-DEVOPS-nomenclature-scripts.md)
- [ADR-608, Non-duplication fonctionnelle transversale](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)
- [Guide de build Apache Jena](../../../BUILD.md)
- [Module d'intégration existant](../../../jena-integration-tests/)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation à la façade exigée par l'issue #2 | Orchestrer Maven, Fuseki réel, fournisseurs réels et Playwright sans prétendre que le Makefile existe déjà |
