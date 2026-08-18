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

adr: 900
title: "Conventions de commentaires et Javadoc des tests d'intégration Java"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [0, 2, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "documentation"
  impact: "medium"
  quality:
    - "maintainability"
    - "reliability"
    - "usability"
    - "compliance"
  reversibility: "easy"
  scope: "tactical"
  tech_areas:
    - "java"
    - "javadoc"
    - "junit"
    - "integration-testing"
    - "jena-graphrag"
tags: ["java", "javadoc", "comments", "tests", "diagnostics"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 900 : Conventions de commentaires et Javadoc des tests d'intégration Java

## Contexte

Les tests Java demandés par l'[issue #2](https://github.com/michel-heon/jena/issues/2) doivent expliquer des contraintes que le code seul ne rend pas toujours évidentes : provenance du corpus, prérequis externes, absence de mocks, non-déterminisme du chat, ownership des processus, nettoyage et protection des secrets.

À l'inverse, des commentaires qui paraphrasent les assertions ou décrivent un état historique deviennent rapidement faux. Les fichiers du module doivent suivre les conventions d'Apache Jena définies dans [`CONTRIBUTING.md`](../../../CONTRIBUTING.md).

## Décision

### Types de commentaires

| Type | Usage |
|------|-------|
| En-tête de licence | En-tête Apache requis par le dépôt |
| Javadoc `/** ... */` | Contrat d'une classe, extension ou helper visible et réutilisable |
| Commentaire `//` ou `/* ... */` | Intention non évidente, contrainte externe, sécurité ou nettoyage |
| `TODO` / `FIXME` | Dette temporaire reliée à une issue ou un ADR |

### Javadoc requise

Une Javadoc utile est requise pour :

- les classes publiques de fixtures, extensions JUnit ou lanceurs de processus ;
- les helpers exposant un contrat de cycle de vie, de nettoyage ou de transaction ;
- les représentations du manifeste et des faits attendus ;
- les méthodes dont le contrat porte sur un fournisseur réel, un délai, un budget, une ressource externe ou un secret ;
- les méthodes package-private consommées par plusieurs suites lorsque leur responsabilité n'est pas évidente.

Elle peut être omise pour une classe de test locale dont le nom et les méthodes `@Test` expriment entièrement le scénario, ainsi que pour les helpers privés triviaux.

### Informations à documenter lorsque pertinentes

- prérequis et variables d'environnement par leur nom, jamais par leur valeur ;
- ownership du serveur, du port, du dataset et du répertoire temporaire ;
- comportement de nettoyage après succès, échec ou interruption ;
- raisons pour lesquelles une assertion porte sur une citation plutôt que sur la formulation du LLM ;
- provenance et licence d'une fixture si le manifeste ne suffit pas ;
- limites de délai, coût ou disponibilité d'un vrai fournisseur ;
- garanties de non-journalisation des secrets et corps sensibles.

### Commentaires d'implémentation

Un commentaire explique pourquoi le code adopte une solution non évidente. Il ne doit pas :

- répéter le nom de la méthode ou l'assertion suivante ;
- annoncer un résultat de test non exécuté ;
- prétendre qu'une cible Make ou une infrastructure existe sans ancrage vérifié ;
- masquer une logique complexe qui devrait être extraite et testée ;
- normaliser l'usage d'un mock dans cette suite.

### Tests non déterministes

Les commentaires et noms de tests décrivent les invariants stables : réponse non vide, citation attendue, statut HTTP, structure JSON, provenance RDF ou nettoyage du processus. Ils ne justifient jamais une assertion sur une phrase exacte d'un LLM réel.

### TODO et FIXME

Le format recommandé est :

```java
// TODO: GH-2 - Add the real-provider prerequisite diagnostic.
// FIXME: ADR-400 - Preserve the expected PDF page range in the corpus assertion.
```

Chaque marqueur contient une référence stable et une action concrète. Il ne doit pas servir à différer un nettoyage de processus, une fuite de secret ou un fallback vers un mock.

### Tags Javadoc

- `@param`, `@return` et `@throws` sont utilisés lorsqu'ils ajoutent un contrat non évident ;
- `@deprecated` accompagne `@Deprecated` et indique une alternative ;
- `{@code ...}` sert aux commandes, variables, préfixes RDF et valeurs littérales ;
- `{@link ...}` relie des types réellement présents ;
- `@author` est interdit conformément aux conventions Jena ;
- les tags vides sont interdits.

## Alternatives

### Javadoc sur chaque méthode de test

Rejetée : les noms de scénarios et assertions devraient rester lisibles sans paraphrase.

### Aucun commentaire dans les tests

Rejetée : les contraintes de processus réels, de fournisseurs et de sécurité demandent parfois une explication locale.

### Copier les critères de l'issue dans chaque classe

Rejetée : l'issue et les ADR restent les sources normatives ; la classe documente seulement sa responsabilité spécifique.

## Conséquences

### Positives

- Les contrats de cycle de vie et de sécurité sont visibles lors de la revue.
- Les commentaires restent centrés sur l'intention et les limites observables.
- Les tests externes sont plus faciles à diagnostiquer sans divulguer de secret.
- La documentation Java reste alignée sur le style Apache Jena.

### Négatives

- Les helpers partagés demandent un effort de documentation supplémentaire.
- Les commentaires doivent être relus lorsque les prérequis ou le cycle de vie évoluent.

## Critères de succès

- toutes les classes publiques réutilisables expliquent leur contrat observable ;
- aucun nouveau tag `@author` ;
- tous les `TODO` et `FIXME` possèdent une référence et une action ;
- aucun commentaire ne contient de secret, de réponse fournisseur complète ou d'affirmation non vérifiée ;
- les commentaires de tests réels décrivent des invariants stables, jamais une phrase LLM exacte.

## Références

- [ADR-000, Processus ADR](./000-META-processus-creation-adr.md)
- [ADR-002, Usage vérifié des agents IA](./002-META-agent-ia-non-hallucination.md)
- [ADR-608, Non-duplication fonctionnelle](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)
- [Conventions de contribution Apache Jena](../../../CONTRIBUTING.md)
- [Javadoc Documentation Comment Specification, JDK 21](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html)
- [Google Java Style Guide, Javadoc](https://google.github.io/styleguide/javaguide.html#s7-javadoc)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation aux tests Java externes | Documenter fournisseurs réels, processus, corpus et sécurité sans bruit narratif |
