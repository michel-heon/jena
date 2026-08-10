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
title: "Bootstrap de configuration des fournisseurs externes"
status: "accepted"
date: 2026-08-10
superseded_by: null
replaces: null
related_adrs: [101, 601, 602, 608]
related_issues: []
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

# ADR 600 : Bootstrap de configuration des fournisseurs externes

## Contexte

Les parcours qui utilisent des fournisseurs externes requierent des parametres de connexion et, selon le fournisseur, des secrets. Ces valeurs sont locales ou injectees par la CI et ne doivent jamais apparaitre dans Git, dans les diagnostics ou dans les rapports de test.

Une configuration publique doit servir de modele, la surcharge utilisateur doit rester locale, et son initialisation doit etre idempotente sans generer de secret. Le module d'integration Jena applique cette politique sans introduire de dependance vers un outil de configuration externe.

## Decision

Le module fournit un bootstrap minimal pour preparer la configuration locale des fournisseurs externes :

```bash
make -C jena-graphrag-integration-tests bootstrap-real-providers
```

Cette cible locale appelle `scripts/graphrag-integration-provider-bootstrap.sh`. Son nom court et explicite est autorise par l'ADR 601; le script conserve la nomenclature partagee. Elle :

1. verifie la disponibilite de Git, Java et Maven, ainsi que la presence du depot et du module Maven ;
2. verifie que `env/.env.user` est ignore par Git ;
3. cree ce fichier depuis `env/.env.user.example` s'il est absent, avec les permissions `0600` ;
4. ne lit pas, ne demande pas et n'affiche pas les valeurs des fournisseurs.

Le fichier versionne `env/.env.user.example` ne contient que les noms de variables requis. `env/.env.user` est le seul emplacement local documente pour les valeurs de configuration. L'operateur le source explicitement dans son shell avant l'execution concernee; le bootstrap ne charge aucun secret dans son propre processus.

Les variables exportees par la CI restent autoritaires pour l'execution concernee. Ce bootstrap n'est pas un gestionnaire de secrets, ne substitue pas les variables definies par un autre fournisseur et ne tente pas de deviner des valeurs de configuration.

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

- L'operateur doit renseigner les valeurs propres a son fournisseur, qui ne peuvent pas etre deduites de maniere sure.
- Le chargement explicite dans le shell est une etape supplementaire avant l'execution.

## Criteres de succes

- deux invocations successives du bootstrap ne modifient pas `env/.env.user` existant ;
- le fichier utilisateur est ignore par Git et l'exemple reste versionnable ;
- aucune sortie ne contient une valeur de variable fournisseur ;
- la cible et le script respectent la nomenclature de l'ADR 601 ;
- l'execution dependante d'un fournisseur echoue explicitement si une variable requise reste absente.

## References

- [ADR-101, fournisseurs reels derriere les SPI GraphRAG](./101-ARCH-adoption-langchain4j-couche-provider.md)
- [ADR-601, nomenclature des cibles et scripts](./601-DEVOPS-nomenclature-scripts.md)
- [ADR-602, Makefile comme orchestrateur](./602-DEVOPS-makefile-orchestrateur.md)
- [ADR-608, non-duplication fonctionnelle transversale](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md)

## Historique

| Date | Changement | Raison |
|------|------------|--------|
| 2026-08-10 | Creation et adoption | Encadrer la configuration locale necessaire aux fournisseurs externes |