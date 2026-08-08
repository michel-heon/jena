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

adr: 608
title: "Non-duplication fonctionnelle dans la qualification GraphRAG"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [101, 400, 601, 602]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "devops"
  impact: "medium"
  quality:
    - "maintainability"
    - "reliability"
  reversibility: "easy"
  scope: "tactical"
  tech_areas:
    - "java"
    - "junit"
    - "playwright"
    - "makefile"
    - "maven"
    - "corpus-manifest"
tags: ["dry", "non-duplication", "fixtures", "orchestration", "tests"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 608 : Non-duplication fonctionnelle dans la qualification GraphRAG

## Contexte

La qualification définie par l'[issue #2](https://github.com/michel-heon/jena/issues/2) traverse Make, Maven, JUnit, Playwright, Fuseki, des fournisseurs externes et un corpus partagé. Les mêmes préoccupations peuvent facilement être recopiées :

- validation des prérequis et noms de variables d'environnement ;
- démarrage, attente et arrêt de Fuseki ;
- chargement du manifeste et résolution des documents du corpus ;
- construction des requêtes API et validation des erreurs ;
- collecte et expurgation des journaux ;
- faits attendus réutilisés par l'ingestion, l'API et le chat.

La duplication augmente le risque que JUnit et Playwright qualifient des contrats différents. L'abstraction prématurée est également risquée : une assertion Java et un parcours navigateur similaires peuvent avoir des objectifs et des cycles de vie distincts.

## Décision

Toute logique fonctionnelle répétée est évaluée dès la deuxième occurrence. À partir de trois occurrences, une extraction ou une justification explicite est obligatoire.

### Sources canoniques

| Information | Source canonique |
|-------------|------------------|
| Documents, provenance, licence et faits attendus | Manifeste du corpus |
| Vocabulaire et propriétés RDF | `GRAG` et `grag.ttl` dans `jena-graphrag` |
| Contrats de production | Services et SPI de `jena-graphrag` |
| Orchestration publique | Makefile racine |
| Build et sélection des tests Java | POM Maven du module |
| Parcours black-box | Tests et configuration Playwright |
| Règles architecturales | ADR indexés dans ce répertoire |

### Ce qui doit être mutualisé

- lecture et validation du manifeste du corpus dans les tests Java ;
- cycle de vie réel de Fuseki lorsqu'il est identique entre plusieurs suites ;
- redaction des secrets et collecte d'artefacts ;
- validation non triviale des prérequis de fournisseurs ;
- construction d'un même dataset de qualification ;
- assertions métier identiques sur les citations ou la provenance.

### Ce qui ne doit pas être dupliqué depuis la production

Les tests utilisent les services de production pour ingérer, indexer, rechercher et répondre. Ils ne réimplémentent pas :

- le chunking PDF ;
- le calcul des URI ou empreintes ;
- la sérialisation des requêtes fournisseur ;
- le scoring ou la recherche vectorielle ;
- la construction des citations GraphRAG.

Une implémentation indépendante n'est acceptable que pour calculer une valeur attendue simple et clairement séparée du chemin de production.

### Ce qui peut rester distinct

- une vérification JUnit détaillée du graphe RDF et un contrôle Playwright du parcours utilisateur ;
- les idiomes courts propres à Java, TypeScript, Bash ou Make ;
- une garde locale plus lisible qu'une abstraction partagée ;
- deux fixtures proches mais destinées à des scénarios ou licences différents.

### Frontières

1. Make orchestre et délègue ; il ne contient pas les assertions.
2. Maven gère le build Java ; Make ne reproduit pas son graphe de dépendances.
3. JUnit possède les invariants métier internes ; Playwright possède les observations black-box.
4. Le manifeste décrit les faits stables ; il ne duplique pas chaque détail RDF d'une fixture.
5. Un helper partagé n'est ajouté qu'avec plusieurs appelants réels et une responsabilité nommable.
6. Une duplication intentionnelle est documentée lorsqu'elle pourrait être confondue avec une omission.

## Alternatives

### Interdire toute duplication

Rejetée : les frontières entre Java, TypeScript, Make et Bash rendent certaines répétitions nécessaires.

### Mutualiser uniquement après dérive

Rejetée : les contrats de sécurité, de corpus et de nettoyage sont trop importants pour attendre une divergence.

### Partager toutes les assertions entre JUnit et Playwright

Rejetée : leurs niveaux d'observation sont différents ; une abstraction commune masquerait cette distinction.

## Conséquences

### Positives

- Le corpus et ses faits attendus restent cohérents entre ingestion, API et chat.
- Le nettoyage des processus et la protection des secrets disposeront chacun d'une implémentation autoritaire lors de leur réalisation.
- Les tests exercent le code de production au lieu de le reproduire.
- Les helpers restent justifiés par des usages concrets.

### Négatives

- Le seuil de trois occurrences exige une revue qualitative.
- Un helper partagé augmente son rayon d'impact et doit être testé.
- Certaines répétitions inter-langages restent intentionnelles.

## Critères de succès

- aucun algorithme de production GraphRAG n'est recopié dans la suite ;
- les faits de corpus partagés ont une source canonique ;
- toute logique non triviale présente au moins trois fois est mutualisée ou justifiée ;
- aucun helper sans plusieurs appelants réels ;
- JUnit et Playwright conservent des responsabilités distinctes et documentées.

## Références

- [ADR-101, Qualification des fournisseurs réels](./101-ARCH-adoption-langchain4j-couche-provider.md)
- [ADR-400, Vocabulaire RDF et corpus](./400-DATA-vocabulaire-rdf-grag.md)
- [ADR-601, Nomenclature](./601-DEVOPS-nomenclature-scripts.md)
- [ADR-602, Makefile orchestrateur](./602-DEVOPS-makefile-orchestrateur.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation aux frontières JUnit/Playwright/Make/Maven | Éviter la dérive sans créer d'abstractions transversales artificielles |
