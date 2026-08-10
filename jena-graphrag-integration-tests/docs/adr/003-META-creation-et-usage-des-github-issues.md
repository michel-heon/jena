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

adr: 3
title: "Creation et usage des GitHub Issues"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [0, 2]
related_issues: []
classification:
  lifecycle: "accepted"
  domain: "meta"
  impact: "medium"
  quality:
    - "maintainability"
    - "reliability"
    - "compliance"
  reversibility: "easy"
  scope: "tactical"
  tech_areas:
    - "git"
    - "github"
    - "documentation"
    - "planning"
tags: ["github", "issues", "triage", "workflow", "traceability"]
stakeholders: ["project maintainers", "contributors"]
effort: "low"
---

# ADR 003 : Creation et usage des GitHub Issues

## Vue d'ensemble

| Attribut | Valeur |
|----------|--------|
| Statut | Accepte |
| Date | 2026-08-10 |
| Impact | Moyen |
| Portee | Gouvernance du suivi du travail |

## Contexte

Le travail d'un projet comprend des decisions ADR, des tranches de realisation,
des validations et des contributions. Sans regles explicites, une issue peut
devenir trop large, ne pas distinguer la cible de l'existant, ou perdre les liens
entre une decision, sa branche, sa validation et sa contribution.

Apache Jena utilise GitHub Issues pour suivre le travail et privilegie les pull
requests pour les contributions. Les discussions de projet sont publiques et
peuvent egalement avoir lieu sur la liste `dev@jena.apache.org`. Cette decision
definit un processus de suivi complementaire aux conventions de contribution du
depot.

## Decision

Les GitHub Issues sont le support canonique du travail actionnable. Une issue
importante est fondee sur des faits verifies, reliee aux ADR et artefacts
pertinents, et conserve les preuves de sa validation avant sa cloture.

### Role et granularite

Une issue represente un besoin, un probleme ou un resultat actionnable unique.
Elle convient a une user story, un bug, une tache technique, une action de
qualite ou une sortie documentaire verifiable. Une decision architecturale
durable reste documentee dans un ADR; une question encore exploratoire releve
d'une discussion ou doit etre cadree avant la creation de l'issue.

Une issue visant plusieurs livrables majeurs est decoupee en issues, sous-issues
ou dependances explicites. Une issue non triviale peut conserver plusieurs
tranches lorsque leurs validations et leur livrable restent communs.

### Verification avant creation ou mise a jour

Avant de creer ou modifier une issue, le redacteur :

1. lit les sources locales et GitHub pertinentes, dont les ADR, le code, les
   issues existantes et les conventions de contribution;
2. recherche les doublons avec des mots-cles discriminants (endpoint, classe,
   livrable, ADR ou symptome);
3. met a jour l'issue existante lorsque le sujet est deja couvert;
4. signale toute incertitude plutot que de l'affirmer comme un fait.

Cette verification applique ADR-002 : aucune phase, API, commande ou validation
ne doit etre declaree existante ou terminee sans preuve reproductible.

### Contenu attendu

Toute issue importante contient :

- un titre concis oriente resultat;
- le contexte verifie et l'objectif;
- le perimetre inclus et hors perimetre;
- des criteres d'acceptation observables;
- les references aux ADR, fichiers, issues et dependances utiles;
- pour un travail non trivial, des tranches avec une validation ciblee;
- la strategie de validation et les preuves attendues pour la cloture.

Les labels, jalons, types et assignations utilisent uniquement les valeurs
existantes verifiees dans GitHub. Ils ne sont jamais inventes pour completer une
issue.

### Tracabilite et cloture

Le corps ou les commentaires d'une issue importante relient les ADR applicables,
la branche de contribution, les commits ou la pull request, ainsi que les
commandes et resultats de validation. Une issue de realisation reste ouverte
tant que le travail n'est pas integre selon le processus Apache Jena. Avant une
cloture pour completion, un commentaire fournit les preuves de validation et
les limites connues. Une cloture pour une autre raison utilise la raison GitHub
appropriee.

## Modele recommande

```markdown
## Objectif
[Resultat attendu en une phrase]

## Contexte verifie
- [ADR, fichier, issue ou source primaire]

## Perimetre inclus
- [ ] [Livrable]

## Hors perimetre
- [Element explicitement exclu]

## Plan de realisation
### Tranche 1 - [Nom]
- [ ] [Action]
Validation ciblee : [test, commande ou preuve]

## Criteres d'acceptation
- [ ] [Critere observable]

## References
- [ADR, issue, branche ou fichier]
```

## Alternatives considerees

### Issues libres

Rejetee : la qualite, la recherche de doublons et la tracabilite deviennent
heterogenes.

### ADR et documentation sans issues

Rejetee : les ADR fixent les decisions mais ne suivent pas les bugs, validations
de tranche et commentaires de cloture.

### Outil de suivi externe

Rejetee : il augmente le cout de tracabilite avec les issues, commits et pull
requests GitHub.

## Consequences

### Positives

- Le besoin, la decision, la contribution et la validation restent relies.
- Les affirmations de l'issue sont verifiables et distinguent l'existant de la
  cible.
- Les contributions suivent les conventions documentees d'Apache Jena.

### Negatives

- Les issues importantes demandent une recherche et une redaction plus
  rigoureuses.
- Le decoupage doit eviter a la fois les issues trop larges et le sur-decoupage.

## Criteres de succes et validation

| Critere | Validation |
|---------|------------|
| Objectif, perimetre et acceptation explicites | Revue de triage |
| Recherche de doublons effectuee | Historique ou commentaire de l'issue |
| ADR et dependances relies lorsque pertinents | Corps ou commentaires de l'issue |
| Preuves de validation avant cloture | Commentaire final |
| Etat du projet fonde sur des sources verifiees | Revue documentaire selon ADR-002 |

## References

- [ADR-000, Processus de creation et de gestion des ADR](./000-META-processus-creation-adr.md)
- [ADR-002, Usage verifie des agents IA et contrainte de non-hallucination](./002-META-agent-ia-non-hallucination.md)
- [Guide de contribution Apache Jena](../../../CONTRIBUTING.md)
- [GitHub Docs - About issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues)
- [GitHub Docs - Configuring issue templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-10 | Import et adaptation au depot Apache Jena | Etablir un suivi d'issue verifiable et conforme aux conventions Apache Jena |