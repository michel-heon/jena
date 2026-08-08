<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements. See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership. The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied. See the License for the
   specific language governing permissions and limitations
   under the License.

   SPDX-License-Identifier: Apache-2.0
-->

# Taxonomie des ADR de jena-graphrag-integration-tests

**Version** : 1.0  
**Date** : 2026-08-08  
**Projet** : `jena-graphrag-integration-tests`  
**Contexte** : tests d'intégration du module Apache Jena [`jena-graphrag`](../../../jena-graphrag/)

Cette taxonomie s'applique en premier lieu au travail défini par l'[issue GitHub #2](https://github.com/michel-heon/jena/issues/2) : module Maven externe, corpus qualifié, ingestion et indexation réelles, API sur Fuseki réel, fournisseurs réels, smoke tests Playwright et orchestration Make.

## Documents complémentaires

Cette taxonomie forme un ensemble cohérent avec :

1. [ADR-000](./000-META-processus-creation-adr.md), qui définit le processus et la numérotation ;
2. [README.md](./README.md), qui indexe les ADR.

Toute évolution des dimensions ou des valeurs doit être répercutée dans ces deux documents.

## Objectif

Chaque ADR est classé selon sept dimensions afin de faciliter :

- la recherche et la revue des décisions ;
- le suivi de leur cycle de vie ;
- l'analyse de leur portée et de leurs risques ;
- l'identification des composants Apache Jena concernés ;
- le traitement automatisé du frontmatter YAML.

## Les sept dimensions

### 1. Lifecycle

État courant de la décision.

| Valeur | Description |
|--------|-------------|
| `draft` | Rédaction en cours ; peut contenir des TODO |
| `proposed` | Document complet soumis à la revue |
| `accepted` | Décision approuvée et applicable |
| `rejected` | Proposition non retenue et conservée pour historique |
| `deprecated` | Décision obsolète sans remplacement direct |
| `superseded` | Décision remplacée par un nouvel ADR |

La valeur doit être identique dans `status` et `classification.lifecycle`.

### 2. Domain

Domaine architectural principal. Un ADR ne possède qu'un domaine principal ; les préoccupations secondaires sont décrites dans `tech_areas` et `quality`.

| Préfixe | Plage | Valeur `domain` | Périmètre dans ce projet |
|---------|-------|-----------------|--------------------------|
| `META` | 000-099 | `meta` | Processus ADR et gouvernance documentaire |
| `ARCH` | 100-199 | `architecture` | Structure et frontières du module de tests |
| `INFRA` | 200-299 | `infrastructure` | Maven, processus, conteneurs et services de test |
| `SEC` | 300-399 | `security` | Secrets, réseau, authentification et isolation |
| `DATA` | 400-499 | `data` | Corpus, fixtures RDF, index et données attendues |
| `API` | 500-599 | `api` | Contrats Fuseki, HTTP, RDF et SPARQL |
| `DEVOPS` | 600-699 | `devops` | CI, profils et automatisation reproductible |
| `TEST` | 700-799 | `test` | Stratégie, niveaux, assertions et qualification |
| `BIZ` | 800-899 | `business` | Critères produit, livraison et acceptation |
| `DOC` | 900-999 | `documentation` | Guides d'exécution et conventions rédactionnelles |

### 3. Impact

Ampleur de la décision.

| Valeur | Critères indicatifs |
|--------|---------------------|
| `low` | Changement local, réversible en moins d'une journée |
| `medium` | Plusieurs scénarios ou composants, effort de un à cinq jours |
| `high` | Effet transversal sur le module, la CI ou les contrats testés |
| `critical` | Gouvernance fondamentale, sécurité ou architecture difficilement réversible |

Exemples :

- `low` : format d'un rapport de test local ;
- `medium` : choix d'un conteneur pour un scénario Fuseki ;
- `high` : séparation entre ingestion sans réseau et qualification avec de vrais services externes ;
- `critical` : règle autorisant ou interdisant les secrets et appels réseau dans la CI par défaut.

### 4. Quality

Attributs de qualité affectés, inspirés d'ISO 25010.

| Valeur | Application aux tests d'intégration GraphRAG |
|--------|----------------------------------------------|
| `performance` | Durée, débit, consommation mémoire et taille des corpus |
| `security` | Secrets, autorisations, TLS, SSRF et exposition réseau |
| `reliability` | Déterminisme, stabilité, reprise et absence de tests intermittents |
| `maintainability` | Lisibilité, mutualisation des fixtures et diagnostic des échecs |
| `cost` | Consommation de services, de calcul ou de stockage externes |
| `usability` | Simplicité d'exécution et qualité des rapports pour les contributeurs |
| `compliance` | Licence Apache 2.0, règles ASF et provenance des corpus |
| `portability` | Exécution sur les environnements de développement et de CI pris en charge |

Au moins une valeur est requise.

### 5. Reversibility

Effort nécessaire pour revenir sur la décision.

| Valeur | Interprétation |
|--------|----------------|
| `easy` | Changement local, moins d'une journée |
| `moderate` | Plusieurs tests ou fixtures, un à cinq jours |
| `hard` | Migration transversale du module ou de la CI |
| `irreversible` | Retour impossible ou prohibitif sans perdre des garanties ou des données |

### 6. Scope

Horizon et niveau de la décision.

| Valeur | Description |
|--------|-------------|
| `strategic` | Orientation durable et transversale |
| `tactical` | Organisation d'une capacité ou d'une suite sur plusieurs versions |
| `operational` | Choix local pour un scénario ou un composant |

### 7. Tech areas

Liste libre de technologies et de composants vérifiés dans le dépôt. Les valeurs suivantes sont recommandées lorsque pertinentes.

#### Projet et tests

- `jena-graphrag`
- `jena-graphrag-integration-tests`
- `integration-testing`
- `junit`
- `maven-surefire`
- `maven-failsafe`
- `testcontainers`
- `playwright`
- `corpus-manifest`
- `black-box-testing`
- `process-lifecycle`
- `make`

L'utilisation d'une valeur ne prouve pas que la dépendance ou l'outil est déjà présent. L'ADR doit distinguer clairement l'état actuel d'une proposition.

#### Apache Jena

- `jena`
- `jena-arq`
- `jena-text`
- `jena-tdb2`
- `jena-fuseki2`
- `jena-fuseki-core`
- `jena-fuseki-main`

#### RDF et GraphRAG

- `rdf`
- `sparql`
- `turtle`
- `assembler`
- `graphrag`
- `ingestion`
- `retrieval`
- `embeddings`
- `vector-search`
- `lucene`

#### Fournisseurs et sécurité

- `embedding-provider`
- `chat-provider`
- `real-provider`
- `external-service`
- `auth`
- `secrets`
- `tls`
- `threat-model`

#### Infrastructure et documentation

- `java`
- `maven`
- `github-actions`
- `docker`
- `documentation`
- `adr`

## Exemple complet

```yaml
---
adr: 700
title: "Séparation de l'ingestion locale et des tests avec fournisseurs réels"
status: "proposed"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: []
related_issues: []

classification:
  lifecycle: "proposed"
  domain: "test"
  impact: "high"
  quality:
    - "reliability"
    - "security"
    - "cost"
  reversibility: "moderate"
  scope: "tactical"
  tech_areas:
    - "jena-graphrag"
    - "integration-testing"
    - "real-provider"
    - "external-service"

tags: ["integration-tests", "hermetic", "external-services"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "medium"
---
```

Cet exemple illustre le format ; il ne constitue pas une décision acceptée et n'affirme pas qu'une suite externe est déjà implémentée. Conformément à l'issue #2, une décision réelle ne peut pas introduire de mock ou de fallback simulé dans cette qualification.

## Correspondance avec les tranches de l'issue #2

| Tranche | Domaine principal probable | Domaines secondaires fréquents |
|---------|----------------------------|--------------------------------|
| Module, corpus et prérequis | `infrastructure` | `data`, `security`, `documentation` |
| Ingestion réelle | `test` | `data`, `architecture` |
| Contrats API sur Fuseki réel | `api` | `test`, `security`, `infrastructure` |
| Embeddings et chat réels | `test` | `security`, `data`, `api`, `cost` |
| Playwright et orchestration | `devops` | `test`, `infrastructure`, `documentation` |

Cette correspondance guide le classement sans imposer la création d'un ADR par tranche. Un ADR n'est créé que lorsqu'une décision structurante présente des alternatives ou des conséquences durables.

## Validation

Avant d'accepter un ADR, vérifier :

- [ ] `status` et `classification.lifecycle` sont identiques ;
- [ ] le numéro appartient à la plage de `classification.domain` ;
- [ ] un seul domaine principal est défini ;
- [ ] au moins un attribut `quality` est présent ;
- [ ] `impact`, `reversibility` et `scope` sont justifiés dans le texte ;
- [ ] les `tech_areas` correspondent à des composants existants ou sont explicitement présentées comme propositions ;
- [ ] les classes, commandes, endpoints et résultats cités ont été vérifiés ;
- [ ] les liens locaux fonctionnent ;
- [ ] l'entrée et les statistiques de [README.md](./README.md) sont à jour.

## Recherches utiles

Depuis `jena-graphrag-integration-tests/docs/adr` :

```bash
# ADR de test.
grep -l 'domain: "test"' ./*.md

# Décisions ayant un impact élevé ou critique.
grep -lE 'impact: "(high|critical)"' ./*.md

# Décisions liées à la sécurité.
grep -l '"security"' ./*.md

# Fichiers d'une catégorie donnée.
ls -1 ./*-TEST-*.md
```

## Convention de nommage

Le format est `XXX-CATEGORIE-titre-kebab-case.md`.

Exemples possibles :

```text
000-META-processus-creation-adr.md
100-ARCH-structure-module-tests-integration.md
200-INFRA-services-test-fuseki.md
300-SEC-isolation-appels-externes.md
400-DATA-corpus-reference-graphrag.md
500-API-contrat-endpoint-answer.md
600-DEVOPS-profils-tests-ci.md
700-TEST-strategie-tests-integration-graphrag.md
900-DOC-guide-diagnostic-echecs.md
```

## Références

- [Issue #2, Créer le module jena-graphrag-integration-tests avec ingestion, API et chat réels](https://github.com/michel-heon/jena/issues/2)
- [ISO/IEC 25010, Systems and software Quality Requirements and Evaluation](https://iso25000.com/index.php/en/iso-25000-standards/iso-25010)
- [Apache Software Foundation, How the ASF works](https://www.apache.org/foundation/how-it-works/)
- [Architecture Decision Records](https://adr.github.io/)
- [Michael Nygard, Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [Documentation Apache Jena](https://jena.apache.org/documentation/)
- [README du module `jena-graphrag`](../../../jena-graphrag/README.md)

## Maintenance

Une modification majeure de cette taxonomie nécessite un nouvel ADR qui supersède ADR-000. Une modification éditoriale sans effet sur la classification peut être apportée directement, avec mise à jour de la version et de la date.

**Maintenu par** : mainteneurs de `jena-graphrag` et contributeurs aux tests d'intégration  
**Dernière mise à jour** : 2026-08-08
