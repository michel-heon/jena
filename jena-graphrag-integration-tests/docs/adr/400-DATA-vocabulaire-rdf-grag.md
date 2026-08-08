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

adr: 400
title: "Vocabulaire RDF mg: comme contrat des corpus et assertions GraphRAG"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [2]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "data"
  impact: "critical"
  quality:
    - "maintainability"
    - "portability"
    - "reliability"
    - "compliance"
  reversibility: "hard"
  scope: "strategic"
  tech_areas:
    - "rdf"
    - "sparql"
    - "turtle"
    - "vocabulary"
    - "jena-graphrag"
    - "corpus-manifest"
tags: ["rdf", "vocabulary", "mg", "corpus", "provenance", "assertions"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---

# ADR 400 : Vocabulaire RDF `mg:` comme contrat des corpus et assertions GraphRAG

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut** | Accepté |
| **Namespace** | `http://ormynet.com/ns/msft-graphrag#` |
| **Classe Java** | `org.apache.jena.vocabulary.GRAG` |
| **Rôle local** | Définir les faits RDF attendus après ingestion et indexation |

## Contexte vérifié

`jena-graphrag` embarque le vocabulaire `mg:` dans [`grag.ttl`](../../../jena-graphrag/src/main/resources/org/apache/jena/graphrag/grag.ttl) et l'expose par la classe Java [`GRAG`](../../../jena-graphrag/src/main/java/org/apache/jena/vocabulary/GRAG.java).

Le vocabulaire courant contient les classes amont `Document`, `Chunk`, `Entity`, `Community`, `Covariate` et `Finding`. Il contient aussi `Relationship`, `source` et `target`, extensions Apache Jena utilisées pour réifier les relations importées. Pour la traçabilité PDF, il expose notamment `sourceHash`, `sourceFile`, `chunkIndex` et `chunkPages`.

L'issue #2 impose un corpus petit, versionné, licencié et traçable, ainsi que la vérification des documents, chunks, textes, URI, liens de provenance, index et citations. Le vocabulaire de production doit donc être la source des assertions ; la suite ne définit pas un modèle RDF parallèle.

## Décision

Les corpus et scénarios de `jena-graphrag-integration-tests` expriment leurs faits attendus avec les termes `GRAG` réellement exposés par `jena-graphrag`.

### Invariants de l'ingestion PDF native

Pour chaque document PDF valide traité par `DocumentIngestionService` :

- une ressource de type `mg:Document` est créée ;
- au moins un `mg:Chunk` est lié au document par `mg:partOf` ;
- chaque chunk possède un texte non vide et un indice cohérent ;
- le document conserve son empreinte et son nom de source ;
- les pages ou plages de pages sont présentes lorsque la source PDF permet de les déterminer ;
- les URI restent stables pour deux ingestions du même contenu selon le contrat de production ;
- la recherche retrouve un chunk relié au document attendu.

### Invariants de l'import RDF

Pour chaque dump RDF valide traité par `GraphRAGImporter` :

- les URI individuelles de la source sont préservées ;
- les termes `snake_case` du namespace `mg:` sont normalisés en `camelCase` ;
- les relations singleton sont réifiées en `mg:Relationship` avec `mg:source` et `mg:target` ;
- l'arête directe `mg:relatedTo` est également produite ;
- les prédicats étrangers au namespace `mg:` sont conservés ;
- importer deux fois le même dump reste idempotent selon la sémantique d'ensemble RDF.

Les propriétés `mg:sourceHash`, `mg:sourceFile`, `mg:chunkIndex` et `mg:chunkPages` sont des garanties de l'ingestion PDF native. Elles ne sont pas exigées d'un dump RDF qui ne les contient pas.

### Relations et contexte

Lorsqu'un corpus contient des relations attendues :

- les relations réifiées sont de type `mg:Relationship` ;
- `mg:source` et `mg:target` pointent vers les entités attendues ;
- les propriétés `mg:description`, `mg:weight` ou `mg:rank` sont vérifiées uniquement lorsqu'elles sont garanties par le corpus et l'importeur ;
- les citations renvoient à des ressources présentes dans le dataset qualifié.

### Manifeste du corpus

Le manifeste sous `src/test/resources/corpus/` décrit, pour chaque fichier :

| Champ | Exigence |
|-------|----------|
| Identifiant | Stable et unique dans le module |
| Chemin | Relatif au corpus |
| Provenance | Auteur ou organisme et URL, si applicable |
| Licence | Identifiant ou texte permettant la redistribution dans le dépôt |
| Empreinte | Permet de détecter une modification involontaire |
| Faits attendus | Documents, chunks, textes, relations ou citations vérifiables |
| Scénarios | `ingestion`, `chat`, `invalid` ou combinaison explicite |
| Chemin d'ingestion | `pdf-native` ou `rdf-import`, avec les invariants applicables |

Le manifeste ne duplique pas l'intégralité du graphe attendu. Il décrit les invariants stables nécessaires aux scénarios, tandis que les fixtures RDF dédiées portent les graphes détaillés lorsque cela améliore la lisibilité.

## Alternatives

### Créer un vocabulaire propre aux tests

Rejetée : les tests ne qualifieraient plus exactement le contrat de production.

### Vérifier uniquement des nombres de triplets

Rejetée : un comptage peut réussir malgré une provenance, un type ou une relation incorrecte.

### Comparer tout le dataset octet pour octet

Rejetée : l'ordre RDF et certaines représentations sérialisées ne sont pas des contrats. Les assertions portent sur le graphe et les invariants observables.

## Conséquences

### Positives

- Le corpus sert de spécification exécutable des invariants de production.
- Les erreurs de namespace, provenance et relations sont détectées de bout en bout.
- Les citations chat peuvent être reliées à des ressources RDF connues.
- La licence et la provenance des entrées deviennent auditables.

### Négatives

- Une évolution intentionnelle de `GRAG` exige l'adaptation coordonnée du manifeste et des assertions.
- Les assertions RDF demandent plus de précision qu'un simple contrôle de réponse HTTP.
- Les corpus externes ne peuvent être ajoutés sans preuve de redistribution compatible.

## Critères de succès

| Critère | Cible |
|---------|-------|
| Fichiers de corpus avec provenance et licence documentées | 100 % |
| PDF valides produisant des chunks reliés et une provenance PDF | 100 % |
| Dumps RDF valides normalisés sans URI individuelle inventée | 100 % |
| Entrées `invalid/` rejetées selon le contrat documenté | 100 % |
| Recherche retrouvant la source attendue | 100 % des scénarios positifs |
| Citations chat pointant vers le corpus | Au moins une par question qualifiée |
| Vocabulaire RDF parallèle propre aux tests | 0 |

## Références

- [ADR-002, Usage vérifié des agents IA](./002-META-agent-ia-non-hallucination.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)
- [`GRAG.java`](../../../jena-graphrag/src/main/java/org/apache/jena/vocabulary/GRAG.java)
- [`grag.ttl`](../../../jena-graphrag/src/main/resources/org/apache/jena/graphrag/grag.ttl)
- [W3C RDF 1.1](https://www.w3.org/TR/rdf11-concepts/)
- [W3C SPARQL 1.1](https://www.w3.org/TR/sparql11-overview/)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation au corpus de qualification | Aligner les assertions sur le vocabulaire courant, y compris `mg:Relationship` et la provenance PDF |
