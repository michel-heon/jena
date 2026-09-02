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

adr: 603
title: "Nomenclature des tags Git du fork jena-graphrag"
status: "proposed"
date: 2026-09-02
superseded_by: null
replaces: null
related_adrs: [601, 602, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"

classification:
  lifecycle: "proposed"
  domain: "devops"
  impact: "medium"
  quality:
    - "maintainability"
    - "usability"
  reversibility: "moderate"
  scope: "tactical"
  tech_areas:
    - "git"
    - "automation"
    - "make"
    - "integration-testing"

tags: ["git", "tags", "naming", "release", "governance"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 603 : Nomenclature des tags Git du fork jena-graphrag

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| **Statut** | Proposé |
| **Date de décision** | 2026-09-02 |
| **Parties prenantes** | Mainteneurs de `jena-graphrag`, contributeurs aux tests d'intégration |
| **Impact** | Moyen |
| **Effort d'implémentation** | Faible |
| **Risque technique** | Faible |

## Contexte et problème

Le dépôt local (`michel-heon/jena`, fork d'`apache/jena`) accumule des tags Git de deux origines distinctes :

1. **Tags hérités d'`apache/jena`** obtenus par `fetch` du remote `upstream` avant sa suppression (voir historique du dépôt), par exemple `jena-2.13.0`, `jena-2.13.0-rc1`, `apache-jena-2.7.0-incubating`, `2.7.3-RC3`. Ces tags suivent les conventions historiques du projet Apache Jena (SemVer-like `jena-MAJOR.MINOR.PATCH`, suffixes `-rcN` ou `-incubating`) et ne doivent pas être recréés ou modifiés localement : ils documentent des releases officielles publiées par l'ASF.
2. **Tags créés localement sur le fork** pour jalonner le travail de l'issue [#2](https://github.com/michel-heon/jena/issues/2), par exemple `issue-2-tranche-1`, `issue-2-tranche-2`, `issue-2-tranche-3`, `issue-2-tranche-4`, `issue-2-tranche-4-bootstrap`, ainsi qu'un tag lié à une autre issue, `issue-5-graphrag-modes`.

Au 2026-09-02, `git tag -l` retourne 115 tags sans distinction visuelle entre ces deux populations. Cette confusion complique :

- l'identification rapide d'un tag comme release officielle ou comme jalon de fork ;
- la suppression sûre des tags devenus obsolètes après la déconnexion du remote `upstream` ;
- la création cohérente de nouveaux jalons pour les futures tranches ou issues du fork.

Le fork ne publie pas de releases indépendantes d'Apache Jena : il n'introduit donc pas de nouveau schéma de version, mais doit clarifier la nomenclature des tags qu'il crée lui-même.

## Décision

Nous formalisons deux familles de tags, en s'appuyant sur les bonnes pratiques Git (tags annotés, [Conventional Commits](https://www.conventionalcommits.org/) et [Semantic Versioning 2.0.0](https://semver.org/)) et sur l'usage déjà observé dans ce dépôt.

### 1. Tags de release upstream (lecture seule)

Format hérité, non modifiable localement : `jena-MAJOR.MINOR.PATCH[-QUALIFIER]` ou héritages historiques (`apache-jena-*`, `MAJOR.MINOR.PATCH-RCn`).

- Ces tags ne sont **jamais créés, renommés ou supprimés** sur le fork : ils appartiennent à l'historique amont.
- Un tag de cette famille présent localement mais dont le remote d'origine (`upstream`) n'est plus configuré peut être supprimé sans risque de perte, car il reste récupérable depuis `apache/jena` en cas de besoin futur.

### 2. Tags de jalon de fork (`issue-<numéro>-<slug>`)

Format : `issue-<numéro-issue>-<slug-kebab-case>[-<sous-jalon>]`.

- `<numéro-issue>` référence l'issue GitHub du fork qui motive le jalon (traçabilité obligatoire, cf. [ADR-003](./003-META-creation-et-usage-des-github-issues.md)) ;
- `<slug-kebab-case>` décrit le contenu du jalon en minuscules ASCII et tirets, sans redondance avec le numéro d'issue ;
- un sous-jalon optionnel (`-bootstrap`, `-rc1`, etc.) précise une étape intermédiaire sans créer une nouvelle issue.

Exemples déjà conformes : `issue-2-tranche-1` à `issue-2-tranche-4`, `issue-2-tranche-4-bootstrap`, `issue-5-graphrag-modes`.

Règles complémentaires :

1. le tag est **annoté** (`git tag -a`), avec un message décrivant l'état du jalon et les tests qui le valident ;
2. le tag n'est créé qu'après qu'une décision ou un livrable stable est atteint, jamais sur un état intermédiaire non testé ;
3. aucun tag de cette famille ne réutilise le préfixe `jena-` ou `apache-jena-`, réservé aux releases officielles ;
4. un tag obsolète (jalon abandonné ou remplacé) est supprimé plutôt que laissé en l'état, avec mention dans l'historique de l'ADR ou de l'issue concernée.

### 3. Nettoyage des tags hérités

Après la déconnexion du remote `upstream`, les tags de la famille 1 qui ne correspondent à aucune référence utilisée par le fork (branches, releases documentées) peuvent être supprimés localement via :

```bash
# Lister les tags candidats à la suppression (hérités d'apache/jena).
git tag -l 'jena-*' 'apache-jena-*' '*-RC*'

# Supprimer un tag local.
git tag -d <nom-du-tag>
```

Cette suppression est locale uniquement : elle n'affecte ni `apache/jena`, ni le remote `origin` du fork, sauf si le tag y a été explicitement poussé (`git push origin :refs/tags/<nom>`), action distincte qui doit être confirmée séparément.

## Alternatives considérées

### Conserver tous les tags sans distinction

Rejetée : perpétue l'ambiguïté déjà observée et complique le nettoyage futur.

### Adopter SemVer strict pour les jalons de fork (`v0.1.0`, `v0.2.0`)

Rejetée pour l'instant : le fork ne publie pas d'artefacts versionnés indépendants ; le lien direct à l'issue GitHub (`issue-<n>-<slug>`) offre une traçabilité plus utile que des versions sans build ni changelog associé.

### Préfixer les jalons de fork par `fork-` plutôt que `issue-`

Rejetée : le préfixe `issue-` est déjà utilisé de façon cohérente sur six tags existants ; changer de convention casserait cette continuité sans bénéfice mesurable.

## Conséquences

### Positives

- Distinction immédiate entre release officielle Apache Jena et jalon de fork par simple lecture du nom du tag.
- Nettoyage sûr des tags hérités devenus inutiles après la suppression du remote `upstream`.
- Continuité avec les tags déjà créés (`issue-2-tranche-*`, `issue-5-graphrag-modes`).

### Négatives

- Nécessite une vérification manuelle ponctuelle pour classer les 115 tags existants avant tout nettoyage.
- Un contributeur doit consulter cet ADR avant de créer un nouveau tag de jalon.

## Plan d'implémentation

1. Documenter cette nomenclature dans le présent ADR et l'indexer dans [README.md](./README.md).
2. Classer les tags existants en deux familles à l'aide des commandes de filtrage ci-dessus.
3. Supprimer, après validation explicite d'un mainteneur, les tags hérités devenus superflus.
4. Appliquer la convention `issue-<numéro>-<slug>` pour tout nouveau jalon de fork.

## Critères de succès et validation

| Métrique | Cible |
|----------|-------|
| Nouveaux tags de jalon conformes au format `issue-<numéro>-<slug>` | 100 % |
| Tags de release upstream recréés ou modifiés localement | 0 |
| Tags annotés (message non vide) parmi les nouveaux jalons | 100 % |
| Suppression de tag hérité effectuée sans confirmation explicite | 0 |

## Traçabilité et liens

- [ADR-003, Création et usage des GitHub Issues](./003-META-creation-et-usage-des-github-issues.md)
- [ADR-601, Nomenclature des cibles et scripts d'intégration GraphRAG](./601-DEVOPS-nomenclature-scripts.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)
- [Semantic Versioning 2.0.0](https://semver.org/)
- [Git, documentation de `git tag`](https://git-scm.com/docs/git-tag)
- [Conventional Commits](https://www.conventionalcommits.org/)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-09-02 | Création de l'ADR après déconnexion du remote `upstream` | Clarifier l'origine et la convention des 115 tags locaux observés |
