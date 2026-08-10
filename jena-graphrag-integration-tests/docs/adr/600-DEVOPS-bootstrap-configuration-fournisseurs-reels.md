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

adr: 600
title: "Bootstrap de configuration des fournisseurs reels"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [101, 601, 602, 608]
related_issues:
  - "https://github.com/michel-heon/jena/issues/2"
classification:
  lifecycle: "accepted"
  domain: "devops"
  impact: "medium"
  quality:
    - "security"
    - "maintainability"
    - "usability"
    - "reproducibility"
  reversibility: "moderate"
  scope: "tactical"
  tech_areas:
    - "bash"
    - "makefile"
    - "environment"
    - "secrets"
    - "integration-testing"
tags: ["bootstrap", "configuration", "providers", "secrets", "integration-tests"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 600 : Bootstrap de configuration des fournisseurs reels

## Contexte

La tranche 4 de l'[issue #2](https://github.com/michel-heon/jena/issues/2) execute les fournisseurs HTTP de production pour les embeddings et le chat. Elle requiert sept variables `GRAPHRAG_*` pour les endpoints, cles API, modeles et la dimension d'embedding. Ces valeurs sont locales ou injectees par la CI et ne doivent jamais apparaitre dans Git, dans les diagnostics ou dans les rapports de test.

Le projet `jena-graphrag-project` applique une decision equivalente dans son ADR 600 : une configuration publique sert de modele, la surcharge utilisateur reste locale, et un bootstrap idempotent ne genere aucun secret. Le module d'integration Jena ne doit pas dependre de ce projet externe ni recopier son generateur multi-profils, qui couvre des consommateurs absents de ce depot.

## Decision

Le module fournit un bootstrap minimal limite au profil de qualification des fournisseurs reels :

```bash
make -C jena-graphrag-integration-tests graphrag-integration-bootstrap-real-providers
```

Cette cible appelle `scripts/graphrag-integration-provider-bootstrap.sh`, conformement a la nomenclature de l'ADR 601. Elle :

1. verifie la disponibilite de Git, Java et Maven, ainsi que la presence du depot et du module Maven ;
2. verifie que `env/.env.user` est ignore par Git ;
3. cree ce fichier depuis `env/.env.user.example` s'il est absent, avec les permissions `0600` ;
4. ne lit pas, ne demande pas et n'affiche pas les valeurs des fournisseurs.

Le fichier versionne `env/.env.user.example` ne contient que les sept noms requis. `env/.env.user` est le seul emplacement local documente pour les valeurs de qualification. L'operateur le source explicitement dans son shell avant le profil Maven; le bootstrap ne charge aucun secret dans son propre processus.

Les variables exportees par la CI restent autoritaires pour son invocation Maven. Ce bootstrap n'est pas un gestionnaire de secrets, ne substitue pas les variables generiques d'un autre fournisseur et ne tente pas de deviner un modele ou une dimension.

## Alternatives considerees

### Secrets dans un fichier RDF ou Java versionne

Rejetee : ces fichiers sont partages, versionnes et souvent inclus dans les diagnostics.

### Chargement implicite de `.env.user` par Maven

Rejetee : un chargement implicite rend la provenance des secrets moins visible et augmente le risque de les propager a des processus non concernes.

### Importer le generateur de `jena-graphrag-project`

Rejetee : il gere des profils et projections propres a ce projet; son import introduirait un couplage inutile et une duplication de responsabilites.

## Consequences

### Positives

- Les noms de variables sont decouvrables sans exposer leurs valeurs.
- La premiere configuration locale est reproductible et idempotente.
- Les secrets restent hors Git et hors des sorties du bootstrap.
- Le mecanisme est limite au module et ne modifie aucune configuration globale du poste.

### Negatives

- L'operateur doit renseigner les modeles et la dimension, qui ne peuvent pas etre deduits de maniere sure.
- Le chargement explicite dans le shell est une etape supplementaire avant la qualification reelle.

## Criteres de succes

- deux invocations successives du bootstrap ne modifient pas `env/.env.user` existant ;
- le fichier utilisateur est ignore par Git et l'exemple reste versionnable ;
- aucune sortie ne contient une valeur de variable fournisseur ;
- la cible et le script respectent la nomenclature de l'ADR 601 ;
- la suite reelle echoue explicitement si une des sept variables reste absente.

## References

- [ADR-101, fournisseurs reels derriere les SPI GraphRAG](./101-ARCH-adoption-langchain4j-couche-provider.md)
- [ADR-601, nomenclature des cibles et scripts](./601-DEVOPS-nomenclature-scripts.md)
- [ADR-602, Makefile comme orchestrateur](./602-DEVOPS-makefile-orchestrateur.md)
- [ADR-608, non-duplication fonctionnelle transversale](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)
- [Issue #2](https://github.com/michel-heon/jena/issues/2)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-10 | Creation et adoption | Encadrer la configuration locale necessaire aux fournisseurs reels de la tranche 4 |