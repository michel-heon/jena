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

adr: 601
title: "Nomenclature des cibles et scripts d'intégration GraphRAG"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: [602, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "devops"
  impact: "low"
  quality:
    - "maintainability"
    - "usability"
  reversibility: "easy"
  scope: "tactical"
  tech_areas:
    - "bash"
    - "automation"
    - "makefile"
    - "maven"
    - "playwright"
tags: ["scripts", "make", "naming", "automation", "integration-tests"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 601 : Nomenclature des cibles et scripts d'intégration GraphRAG

## Contexte

L'issue [#2](https://github.com/michel-heon/jena/issues/2) prévoit une façade Make pour construire, démarrer, attendre, tester et arrêter les processus de qualification. Au 2026-08-08, le dépôt Jena ne possède pas encore de Makefile racine ni de scripts dédiés à ce module. Leur nomenclature doit être fixée avant leur création pour éviter des commandes concurrentes ou ambiguës.

## Décision

### Cibles Make publiques

Les cibles de la façade Make racine utilisent le préfixe `graphrag-integration-` et le kebab-case.

| Cible | Intention stable |
|-------|------------------|
| `graphrag-integration-ingestion` | Qualifier l'ingestion et la récupération sans réseau externe lorsqu'il n'est pas requis |
| `graphrag-integration-api` | Qualifier les contrats API sur un vrai Fuseki |
| `graphrag-integration-chat` | Qualifier embeddings et chat avec de vrais fournisseurs |
| `graphrag-integration-smoke` | Exécuter les parcours Playwright black-box |
| `graphrag-integration` | Agréger la qualification complète documentée |

Les cibles de cycle de vie internes ou publiques suivent le même préfixe et un verbe explicite, par exemple `graphrag-integration-start`, `graphrag-integration-wait` et `graphrag-integration-stop`, seulement si elles sont effectivement nécessaires et documentées.

Une cible d'un Makefile de module, invoquée avec `make -C module`, peut employer un nom plus court si celui-ci reste explicite et ne prétend pas être une façade racine. Par exemple, `bootstrap-real-providers` prépare la configuration locale d'un seul module. Cette exception évite de répéter le contexte déjà porté par le chemin du module, sans introduire les noms ambigus `api`, `chat` ou `smoke`.

### Scripts

Un script n'est créé que lorsque la logique dépasse une invocation lisible dans le Makefile. Son nom suit :

```text
graphrag-integration-{objet}-{action}.sh
```

Exemples admissibles si ces responsabilités sont introduites :

```text
graphrag-integration-provider-check.sh
graphrag-integration-fuseki-start.sh
graphrag-integration-fuseki-stop.sh
graphrag-integration-artifacts-sanitize.sh
```

Règles :

1. minuscules ASCII et tirets uniquement ;
2. nom décrivant un résultat observable ;
3. extension `.sh` pour Bash ;
4. aucun numéro d'ordre : les dépendances sont exprimées par Make ou Maven ;
5. aucun nom générique tel que `utils.sh` ou `run.sh` ;
6. un script interne partagé peut commencer par `_` seulement si plusieurs appelants justifient son existence selon ADR-608.

### Variables d'environnement

Les variables propres à la suite utilisent le préfixe `GRAPHRAG_`. Les noms existants consommés par le code de production sont réutilisés plutôt que renommés. Un nom de variable ne contient jamais sa valeur dans les diagnostics ; les sorties indiquent uniquement si le prérequis est présent, absent ou invalide.

### Artefacts d'échec

Les répertoires et fichiers utilisent des noms descriptifs stables : `playwright-report`, `test-results`, `fuseki.log` ou équivalent défini par l'outil. Les chemins réels sont documentés avec les commandes qui les produisent et sont nettoyés conformément à la politique du module.

## Alternatives

### Cibles courtes comme `api`, `chat` ou `smoke`

Rejetée : elles risquent d'entrer en collision avec d'autres modules du dépôt Jena.

### Un script monolithique numéroté

Rejetée : il masque le cycle de vie, rend le nettoyage difficile et encode l'ordre dans le nom.

### Noms libres au fil des tranches

Rejetée : la façade publique deviendrait difficile à découvrir et à documenter.

## Conséquences

### Positives

- Les commandes sont identifiables comme appartenant à la qualification GraphRAG.
- L'agrégateur et les suites ciblées forment une interface prévisible.
- Les scripts éventuels restent focalisés et découvrables.

### Négatives

- Les noms publics sont plus longs.
- Un renommage ultérieur impose une mise à jour atomique du Makefile, de la CI et de la documentation.

## Critères de succès

- toutes les cibles publiques de l'issue #2 utilisent `graphrag-integration-` ;
- toutes les cibles annoncées dans la documentation existent réellement ;
- aucun ordre d'exécution ne dépend du tri lexical des scripts ;
- aucune sortie de diagnostic n'affiche la valeur d'un secret ;
- les appels et la documentation sont mis à jour dans le même changement qu'un renommage.

## Références

- [ADR-602, Makefile comme orchestrateur standard](./602-DEVOPS-makefile-orchestrateur.md)
- [ADR-608, Non-duplication fonctionnelle transversale](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation aux commandes de l'issue #2 | Définir la nomenclature avant la création du Makefile et des scripts |
