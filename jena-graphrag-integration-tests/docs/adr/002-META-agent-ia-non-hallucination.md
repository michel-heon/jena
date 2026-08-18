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

adr: 2
title: "Usage vérifié des agents IA et contrainte de non-hallucination"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [0]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "meta"
  impact: "high"
  quality:
    - "reliability"
    - "maintainability"
    - "compliance"
  reversibility: "easy"
  scope: "strategic"
  tech_areas:
    - "documentation"
    - "jena-graphrag"
    - "integration-testing"
    - "rdf"
tags: ["ai", "hallucination", "governance", "verification", "integration-tests"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 002 : Usage vérifié des agents IA et contrainte de non-hallucination

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut** | Accepté |
| **Date** | 2026-08-08 |
| **Impact** | Élevé |
| **Portée** | Code, corpus, configuration, tests, automatisation et documentation |

## Contexte

L'[issue #2](https://github.com/michel-heon/jena/issues/2) demande une qualification de bout en bout de `jena-graphrag` : ingestion RDF/PDF, indexation, API sur un vrai processus Fuseki, fournisseurs réels d'embeddings et de chat, et smoke tests Playwright. Ce périmètre expose particulièrement les contributions assistées par IA aux erreurs suivantes :

- citer une classe, une propriété RDF ou un endpoint qui n'existe pas ;
- confondre un comportement de mock avec celui d'un fournisseur réel ;
- inventer une variable d'environnement, une cible Make ou un résultat de test ;
- déclarer une tranche implémentée sans commande ni résultat vérifiable ;
- produire un corpus dont la provenance, la licence ou les faits attendus sont supposés ;
- recopier une décision historique devenue incohérente avec le code courant.

Une telle erreur invaliderait précisément le rôle de ce module : qualifier le livrable réel depuis une surface externe aux tests unitaires de `jena-graphrag`.

## Décision

Tout contenu généré ou modifié avec l'aide d'une IA doit s'appuyer sur le code présent dans le dépôt, un résultat de commande reproductible ou une source primaire officielle.

### Règles opérationnelles

1. **Vérifier le dépôt avant d'affirmer**
   - Les classes et ressources de production sont vérifiées sous [`jena-graphrag`](../../../jena-graphrag/).
   - Le profil Maven est vérifié dans le [`pom.xml` racine](../../../pom.xml).
   - Une cible Make n'est documentée comme disponible que si elle existe réellement dans le Makefile.
   - Un scénario n'est déclaré réussi qu'avec une commande et un résultat observés.

2. **Ne jamais inventer**
   - aucune classe Java, méthode, propriété RDF, opération Fuseki ou structure JSON fictive ;
   - aucune variable d'environnement ou valeur par défaut non confirmée ;
   - aucun fournisseur, modèle ou comportement réseau supposé ;
   - aucune provenance, licence ou assertion de corpus sans manifeste vérifiable.

3. **Distinguer l'existant de la cible**
   - Les exigences de l'issue #2 sont décrites comme cible tant que le code correspondant n'existe pas.
   - Les ADR importés sont adaptés aux versions réellement présentes dans le dépôt.
   - Une incertitude est signalée explicitement et n'est pas transformée en contrat de test.

4. **Prouver l'absence de mocks**
   - Les suites de ce module ne référencent aucune classe `Mock*`, aucun faux serveur HTTP et aucune réponse LLM préfabriquée.
   - Un test de fournisseur réel échoue rapidement si sa configuration obligatoire manque ; il ne bascule jamais silencieusement vers les valeurs hermétiques de production.

5. **Protéger les secrets**
   - Seuls les noms des variables d'environnement sont documentés.
   - Les valeurs de secrets ne sont ni lues par un agent, ni inscrites dans le dépôt, ni reproduites dans les journaux, traces ou rapports.

6. **Revue humaine**
   - Les contrats API, assertions RDF, commandes d'orchestration et règles de sécurité sont relus avant intégration.
   - Les résultats non reproductibles ou dépendants d'un service externe sont accompagnés de leur environnement et de leurs limites.

## Sources autoritaires

| Sujet | Source prioritaire |
|-------|--------------------|
| Comportement de `jena-graphrag` | Code et tests sous [`jena-graphrag`](../../../jena-graphrag/) |
| Build multi-module | [`pom.xml` racine](../../../pom.xml) et Maven |
| Conventions Apache Jena | [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) et documentation Jena |
| Sécurité | [`THREAT_MODEL.md`](../../../THREAT_MODEL.md) et code de configuration |
| RDF/SPARQL | Spécifications W3C et ressources RDF du module |
| LangChain4j | Dépendances résolues, code appelant et documentation officielle de la version utilisée |
| Travail à réaliser | [Issue #2](https://github.com/michel-heon/jena/issues/2) |

## Conséquences

### Positives

- Les scénarios qualifient le comportement réel au lieu d'un système imaginé.
- Les divergences entre ADR historiques et code courant sont détectées pendant l'import.
- Les résultats sont auditables et compatibles avec une revue Apache Jena.
- La sécurité des secrets et l'interdiction des fallbacks simulés deviennent vérifiables.

### Négatives

- La rédaction demande davantage de lectures et d'exécutions ciblées.
- Certaines décisions acceptées restent à implémenter ; leur état de réalisation doit demeurer explicite.
- Les affirmations historiques doivent être réévaluées lors des évolutions du code.

## Critères de succès

| Critère | Cible |
|---------|-------|
| Références techniques ancrées dans le dépôt ou une source primaire | 100 % |
| API, propriétés RDF et cibles Make fictives | 0 |
| Scénarios réels utilisant une classe ou un serveur mock | 0 |
| Secrets présents dans le dépôt ou les sorties | 0 |
| Résultats déclarés sans commande reproductible | 0 |

## Références

- [ADR-000, Processus de création et de gestion des ADR](./000-META-processus-creation-adr.md)
- [Issue #2, module d'intégration GraphRAG réel](https://github.com/michel-heon/jena/issues/2)
- [Documentation Apache Jena](https://jena.apache.org/documentation/)
- [W3C RDF 1.1](https://www.w3.org/TR/rdf11-concepts/)
- [W3C SPARQL 1.1](https://www.w3.org/TR/sparql11-overview/)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation à `jena-graphrag-integration-tests` | Encadrer la qualification réelle demandée par l'issue #2 |
