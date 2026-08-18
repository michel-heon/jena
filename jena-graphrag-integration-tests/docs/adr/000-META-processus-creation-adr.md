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

adr: 0
title: "Processus de création et de gestion des ADR"
status: "accepted"
date: 2026-08-08
superseded_by: null
replaces: null
related_adrs: []
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"

classification:
  lifecycle: "accepted"
  domain: "meta"
  impact: "critical"
  quality:
    - "maintainability"
    - "compliance"
  reversibility: "hard"
  scope: "strategic"
  tech_areas:
    - "documentation"
    - "adr"
    - "integration-testing"
    - "git"

tags: ["process", "documentation", "meta-adr", "governance", "integration-tests"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 000 : Processus de création et de gestion des ADR

## Documents complémentaires obligatoires

Ce processus forme un ensemble cohérent de trois documents :

1. **[ADR-000](./000-META-processus-creation-adr.md)** : processus et règles ;
2. **[TAXONOMY.md](./TAXONOMY.md)** : classification détaillée ;
3. **[README.md](./README.md)** : index et guide rapide.

Toute modification des plages de numérotation, des domaines ou de la classification doit être répercutée dans ces trois documents.

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut** | Accepté |
| **Date de décision** | 2026-08-08 |
| **Parties prenantes** | Mainteneurs de `jena-graphrag`, contributeurs aux tests d'intégration |
| **Impact** | Critique |
| **Effort d'implémentation** | Faible |
| **Risque technique** | Faible |

## Contexte

Le répertoire `jena-graphrag-integration-tests/` est destiné aux tests qui valident `jena-graphrag` avec des composants ou des infrastructures situés hors de la portée de ses tests unitaires. Il doit pouvoir documenter les décisions concernant notamment :

- les parcours d'ingestion, d'indexation et de récupération de bout en bout ;
- l'intégration avec Fuseki et les autres modules Apache Jena ;
- la séparation entre fournisseurs déterministes, services locaux et services externes ;
- les corpus, fixtures et environnements de test ;
- les conditions d'exécution, d'isolation et de reproductibilité des tests.

L'[issue GitHub #2](https://github.com/michel-heon/jena/issues/2) définit le mandat initial du module : qualifier un livrable assemblé avec des composants de production, un vrai processus Fuseki et de vrais fournisseurs d'embeddings et de chat. Le travail est organisé en cinq tranches :

1. module Maven, corpus et validation des prérequis ;
2. ingestion RDF/PDF et indexation réelles ;
3. contrats API contre Fuseki réel ;
4. embeddings et chat GraphRAG réels ;
5. smoke tests Playwright et orchestration par le Makefile racine.

Les invariants imposés par cette issue sont structurants : aucun `Mock*`, faux serveur ou fallback simulé ; secrets exclusivement transmis par variables d'environnement et absents des sorties ; corpus petit, versionné, traçable et licencié ; nettoyage des processus et fichiers temporaires après succès comme après échec. Les assertions sur un LLM non déterministe portent sur la présence d'une réponse et sur les citations attendues, jamais sur une formulation exacte.

Au 2026-08-08, le profil Maven `graphrag` référence `jena-graphrag` mais pas encore ce nouveau module. Le présent répertoire documentaire constitue donc un socle de réalisation, pas la preuve que les tranches de l'issue sont implémentées.

Les ADR capturent le pourquoi des décisions structurantes. Ils facilitent la revue, l'évolution des tests et l'alignement avec les conventions du dépôt Apache Jena. Sans processus commun, les choix d'infrastructure, de périmètre ou de fiabilité risquent de rester implicites ou de diverger du comportement réel de [`jena-graphrag`](../../../jena-graphrag/).

## Décision

Nous adoptons un processus formalisé de création et de gestion des ADR inspiré du modèle Michael Nygard et adapté à `jena-graphrag-integration-tests`.

### Structure obligatoire

Chaque ADR contient un frontmatter YAML lisible par machine :

```yaml
---
adr: XXX
title: "Titre descriptif"
status: "proposed"
date: YYYY-MM-DD
superseded_by: null
replaces: null
related_adrs: []
related_issues: []
classification:
  lifecycle: "proposed"
  domain: "test"
  impact: "medium"
  quality: ["reliability", "maintainability"]
  reversibility: "moderate"
  scope: "tactical"
  tech_areas: ["jena-graphrag", "junit", "integration-testing"]
tags: ["integration-tests"]
stakeholders: ["jena-graphrag maintainers"]
effort: "medium"
---
```

Il contient aussi les sections lisibles par les contributeurs :

```markdown
# ADR XXX : Titre court et descriptif

## Vue d'ensemble
## Contexte et problème
## Décision
## Alternatives considérées
## Conséquences
## Plan d'implémentation
## Critères de succès et validation
## Traçabilité et liens
```

Une matrice de décision quantifiée est ajoutée lorsque plusieurs options crédibles doivent être comparées.

### Numérotation et nommage

Le format est `XXX-CATEGORIE-titre-kebab-case.md` :

- `XXX` est un numéro sur trois chiffres dans la plage de la catégorie ;
- `CATEGORIE` est le préfixe de domaine en majuscules ;
- le titre est descriptif, en minuscules et séparé par des tirets.

| Préfixe | Plage | Domaine | Exemples pour les tests d'intégration GraphRAG |
|---------|-------|---------|------------------------------------------------|
| `META` | 000-099 | Processus | Gouvernance ADR, règles de validation documentaire |
| `ARCH` | 100-199 | Architecture | Structure du module et frontières avec `jena-graphrag` |
| `INFRA` | 200-299 | Infrastructure | Build Maven, conteneurs, services de test |
| `SEC` | 300-399 | Sécurité | Secrets, réseau, isolation des fournisseurs externes |
| `DATA` | 400-499 | Données | Corpus, fixtures RDF, index et jeux de référence |
| `API` | 500-599 | API | Parcours Fuseki et contrats HTTP/SPARQL testés |
| `DEVOPS` | 600-699 | DevOps | CI, profils Maven et exécution reproductible |
| `TEST` | 700-799 | Tests | Stratégie, niveaux, assertions et qualification |
| `BIZ` | 800-899 | Produit | Critères d'acceptation et périmètre de livraison |
| `DOC` | 900-999 | Documentation | Guides d'exécution et conventions rédactionnelles |

Le préfixe doit correspondre à la plage. Si plusieurs domaines sont concernés, le domaine principal détermine le numéro et les domaines secondaires figurent dans `tech_areas`.

### Cycle de vie

| État | Valeur YAML | Description |
|------|-------------|-------------|
| Brouillon | `draft` | Rédaction en cours |
| Proposé | `proposed` | Prêt pour revue |
| Accepté | `accepted` | Décision approuvée et applicable |
| Rejeté | `rejected` | Proposition refusée et conservée pour historique |
| Déprécié | `deprecated` | Décision obsolète sans remplacement direct |
| Supersédé | `superseded` | Décision remplacée par un autre ADR |

## Processus de création

### 1. Identifier le besoin

Un ADR est requis pour une décision durable ou difficile à inverser, par exemple :

- définir la frontière entre tests unitaires et tests d'intégration ;
- choisir une infrastructure externe ou conteneurisée ;
- fixer une stratégie de corpus, d'index ou de nettoyage ;
- modifier les garanties de reproductibilité, d'isolation ou de sécurité ;
- établir un contrat de test entre `jena-graphrag`, Fuseki et un fournisseur.
- répartir les responsabilités entre JUnit et Playwright ;
- définir le manifeste de provenance, de licence et de faits attendus du corpus ;
- fixer les règles de démarrage, d'attente et d'arrêt des processus réels.

Un ADR n'est pas requis pour une correction locale, une préférence cosmétique ou un détail de mise en œuvre sans alternative structurante.

### 2. Créer le fichier

Depuis la racine du dépôt Jena :

```bash
cd jena-graphrag-integration-tests/docs/adr

# Exemple : trouver le dernier ADR de test.
ls -1 7*.md 2>/dev/null | tail -1

# Créer le prochain fichier, puis compléter le frontmatter et les sections.
$EDITOR 700-TEST-strategie-tests-integration-graphrag.md

# Ajouter immédiatement l'ADR à l'index.
$EDITOR README.md
```

### 3. Rédiger et vérifier

1. Décrire le problème et le comportement observé ou recherché.
2. Vérifier dans le code les modules, classes, commandes et propriétés cités.
3. Comparer les alternatives crédibles.
4. Expliquer la décision et ses conséquences positives et négatives.
5. Définir des critères de succès mesurables.
6. Ajouter les liens vers les issues, PR, tests et autres ADR concernés.
7. Mettre à jour [README.md](./README.md), y compris les statistiques.

Les références techniques doivent pointer vers des fichiers, symboles ou documentations vérifiables. Une API, une classe, un endpoint ou un résultat de test ne doit jamais être inventé.

### 4. Revoir et accepter

Avant l'acceptation :

- le frontmatter YAML est complet et cohérent avec [TAXONOMY.md](./TAXONOMY.md) ;
- aucun placeholder ni TODO ne subsiste ;
- les références vers le code et les commandes ont été vérifiées ;
- les conséquences et les risques sont explicités ;
- les critères de succès sont mesurables ;
- au moins un mainteneur revoit une décision d'impact `high` ou `critical`.

### 5. Faire évoluer une décision

Une décision acceptée n'est pas réécrite pour changer son sens. Il faut :

1. créer un nouvel ADR ;
2. marquer l'ancien comme `superseded` ;
3. renseigner `superseded_by` et `replaces` dans les deux documents ;
4. mettre à jour l'index.

## Conséquences

### Positives

- Les choix des tests d'intégration restent traçables.
- Les dépendances entre `jena-graphrag` et l'infrastructure de test sont explicites.
- Les nouveaux contributeurs disposent d'un historique vérifiable.
- La documentation reste proche du code qu'elle gouverne.

### Négatives

- Chaque décision structurante demande un effort de rédaction et de revue.
- L'index et la taxonomie doivent rester synchronisés.

## Critères de succès

| Métrique | Cible |
|----------|-------|
| ADR avec frontmatter YAML complet | 100 % |
| ADR présents dans l'index | 100 % |
| ADR techniques référençant du code, des tests ou une source primaire | 100 % |
| ADR d'impact élevé revus par un mainteneur | 100 % |
| Liens locaux valides | 100 % |

## Références

- [Issue #2, Créer le module jena-graphrag-integration-tests avec ingestion, API et chat réels](https://github.com/michel-heon/jena/issues/2)
- [Architecture Decision Records](https://adr.github.io/)
- [Michael Nygard, Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [Apache Jena, documentation](https://jena.apache.org/documentation/)
- [README du module `jena-graphrag`](../../../jena-graphrag/README.md)
- [Modèle de menaces Apache Jena](../../../THREAT_MODEL.md)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-08 | Import et adaptation du processus au répertoire `jena-graphrag-integration-tests` | Établir la gouvernance documentaire des tests d'intégration GraphRAG |
