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

# Architecture Decision Records de jena-graphrag-integration-tests

Cet index recense les décisions architecturales propres aux tests d'intégration de [`jena-graphrag`](../../../jena-graphrag/). Il couvre les tests qui traversent plusieurs modules Apache Jena ou qui nécessitent une infrastructure absente des tests unitaires du module principal.

Le répertoire documente les décisions ; la présence d'un scénario dans un ADR ne signifie pas qu'il est déjà implémenté. Le code et les résultats de test restent les sources de vérité pour l'état de l'implémentation.

## Contexte de réalisation

L'[issue GitHub #2](https://github.com/michel-heon/jena/issues/2) porte la création de ce module Maven. Son objectif est de qualifier de bout en bout :

- l'ingestion réelle de corpus RDF et PDF, le chunking, la provenance, l'indexation et la récupération ;
- les contrats des API GraphRAG sur un vrai processus Fuseki ;
- la réponse GraphRAG avec de vrais fournisseurs d'embeddings et de chat ;
- le démarrage du livrable, `/$/ping`, l'UI effectivement livrée et un parcours question/réponse cité avec Playwright ;
- l'orchestration reproductible de ces scénarios depuis le Makefile racine.

Le module cible doit être déclaré après `jena-graphrag` dans le profil Maven `graphrag`. Son corpus versionné est séparé en jeux `ingestion/`, `chat/` et `invalid/`, accompagnés d'un manifeste de provenance, de licence et de faits attendus.

### Invariants de l'issue

- Aucun mock, faux serveur HTTP, réponse LLM préfabriquée ou fallback implicite vers un fournisseur simulé.
- Les secrets proviennent uniquement de variables d'environnement et ne figurent ni dans le dépôt ni dans les journaux, traces ou rapports.
- JUnit couvre l'intégration Java et les invariants métier ; Playwright couvre les parcours black-box.
- Les assertions de chat vérifient une réponse non vide et les citations du corpus, sans imposer une phrase exacte.
- Les processus et fichiers temporaires sont nettoyés après succès, échec ou interruption.
- Une configuration de fournisseur incomplète provoque un échec rapide et explicite.

### Hors périmètre initial

Le travail ne déplace pas les tests unitaires de `jena-graphrag`, ne constitue pas un benchmark de modèles, n'ajoute pas d'UI absente du livrable et ne certifie pas tous les fournisseurs dès la première livraison.

### État vérifié au 2026-08-08

Le profil Maven `graphrag` contient actuellement `jena-graphrag` uniquement. Le nouveau répertoire contient ce socle ADR, mais pas encore le `pom.xml`, les corpus, les suites JUnit et Playwright ni l'orchestration Make décrits par l'issue.

Le statut `accepted` signifie que la décision importée et adaptée est retenue comme règle de réalisation. Il ne signifie pas que son implémentation existe déjà ; chaque ADR qui décrit une cible future doit le signaler explicitement.

### Couverture des décisions

| Besoin de l'issue #2 | ADR actuels | Couverture |
|----------------------|-------------|------------|
| Gouvernance et vérification des faits | ADR-000, ADR-002 | Couverte |
| Fournisseurs réels, sans mock ni fallback | ADR-101, ADR-602 | Couverte |
| Corpus, RDF/PDF, provenance et assertions | ADR-400, ADR-608 | Couverte |
| Nommage et orchestration Make | ADR-601, ADR-602 | Couverte |
| Répartition JUnit / Playwright et non-duplication | ADR-608, ADR-900 | Partielle |
| Structure Maven et intégration au profil `graphrag` | Aucun ADR dédié | À décider |
| Contrats détaillés des endpoints Fuseki | Aucun ADR dédié | À décider |
| Sécurité des services réels, secrets et artefacts | ADR-002, ADR-101, ADR-602 | Partielle |
| Stratégie globale des suites, prérequis et politiques d'échec | Aucun ADR TEST dédié | À décider |

L'ensemble actuel est cohérent mais incomplet pour lancer l'implémentation sans décisions supplémentaires. Les prochains ADR structurants attendus sont :

- un ADR `INFRA` pour le module Maven, son ordre dans le profil et le cycle Maven des tests d'intégration ;
- un ADR `SEC` pour le réseau, les secrets, l'expurgation des traces et les fournisseurs externes ;
- un ADR `API` pour la matrice exacte des routes, méthodes, statuts et schémas observables ;
- un ADR `TEST` pour les suites JUnit/Playwright, les prérequis, les catégories et la politique d'échec.

## Documents du système ADR

| Document | Description |
|----------|-------------|
| [ADR-000](./000-META-processus-creation-adr.md) | Processus de création, de revue et d'évolution des ADR |
| [TAXONOMY.md](./TAXONOMY.md) | Classification en sept dimensions |
| [README.md](./README.md) | Index et guide rapide |

## Créer un ADR

Depuis la racine du dépôt Jena :

```bash
cd jena-graphrag-integration-tests/docs/adr

# Choisir la plage dans TAXONOMY.md et trouver le dernier numéro utilisé.
ls -1 7*.md 2>/dev/null | tail -1

# Créer l'ADR, puis l'ajouter à cet index.
$EDITOR 700-TEST-strategie-tests-integration-graphrag.md
$EDITOR README.md
```

Le fichier doit respecter [ADR-000](./000-META-processus-creation-adr.md), contenir un frontmatter YAML complet et ne citer que des éléments vérifiés dans le dépôt ou dans une source primaire.

## Index des ADR

### META - Processus et gouvernance (000-099)

| ADR | Titre | Statut | Date | Domaine |
|-----|-------|--------|------|---------|
| [000](./000-META-processus-creation-adr.md) | Processus de création et de gestion des ADR | Accepté | 2026-08-08 | Méta-processus |
| [002](./002-META-agent-ia-non-hallucination.md) | Usage vérifié des agents IA et contrainte de non-hallucination | Accepté | 2026-08-08 | Méta-processus |

Le répertoire source consulté le 2026-08-08 ne contient aucun ADR-001. Aucun document n'a été créé artificiellement pour combler ce numéro.

### ARCH - Architecture (100-199)

| ADR | Titre | Statut | Date | Domaine |
|-----|-------|--------|------|---------|
| [101](./101-ARCH-adoption-langchain4j-couche-provider.md) | Qualification des fournisseurs réels derrière les SPI GraphRAG | Accepté | 2026-08-08 | Architecture |

### DATA - Données et corpus (400-499)

| ADR | Titre | Statut | Date | Domaine |
|-----|-------|--------|------|---------|
| [400](./400-DATA-vocabulaire-rdf-grag.md) | Vocabulaire RDF `mg:` comme contrat des corpus et assertions GraphRAG | Accepté | 2026-08-08 | Données |

### DEVOPS - Orchestration et automatisation (600-699)

| ADR | Titre | Statut | Date | Domaine |
|-----|-------|--------|------|---------|
| [601](./601-DEVOPS-nomenclature-scripts.md) | Nomenclature des cibles et scripts d'intégration GraphRAG | Accepté | 2026-08-08 | DevOps |
| [602](./602-DEVOPS-makefile-orchestrateur.md) | Makefile racine comme orchestrateur des tests d'intégration GraphRAG | Accepté | 2026-08-08 | DevOps |
| [608](./608-DEVOPS-non-duplication-fonctionnelle-transversale.md) | Non-duplication fonctionnelle dans la qualification GraphRAG | Accepté | 2026-08-08 | DevOps |

### DOC - Documentation Java (900-999)

| ADR | Titre | Statut | Date | Domaine |
|-----|-------|--------|------|---------|
| [900](./900-DOC-conventions-commentaires-java.md) | Conventions de commentaires et Javadoc des tests d'intégration Java | Accepté | 2026-08-08 | Documentation |

## Statistiques

| Indicateur | Valeur |
|------------|--------|
| **Total** | 8 |
| **Acceptés** | 8 |
| **Proposés** | 0 |
| **Brouillons** | 0 |
| **Dépréciés ou supersédés** | 0 |
| **Par domaine** | META : 2, ARCH : 1, DATA : 1, DEVOPS : 3, DOC : 1 |

## Numérotation

| Préfixe | Plage | Domaine | Prochain numéro disponible |
|---------|-------|---------|----------------------------|
| `META` | 000-099 | Processus et gouvernance | 003 |
| `ARCH` | 100-199 | Architecture du module de tests | 102 |
| `INFRA` | 200-299 | Build et infrastructure de test | 200 |
| `SEC` | 300-399 | Sécurité et isolation | 300 |
| `DATA` | 400-499 | Corpus, fixtures et index | 401 |
| `API` | 500-599 | Contrats HTTP, Fuseki et SPARQL | 500 |
| `DEVOPS` | 600-699 | CI et automatisation | 609 |
| `TEST` | 700-799 | Stratégie et qualification | 700 |
| `BIZ` | 800-899 | Critères produit et livraison | 800 |
| `DOC` | 900-999 | Documentation | 901 |

## Statuts

| Statut | Valeur YAML | Description |
|--------|-------------|-------------|
| Brouillon | `draft` | Rédaction en cours |
| Proposé | `proposed` | Prêt pour revue |
| Accepté | `accepted` | Décision approuvée et en vigueur |
| Rejeté | `rejected` | Proposition non retenue |
| Déprécié | `deprecated` | Décision obsolète |
| Supersédé | `superseded` | Décision remplacée par un nouvel ADR |

## Ressources

- [Issue GitHub #2](https://github.com/michel-heon/jena/issues/2)
- [Module `jena-graphrag`](../../../jena-graphrag/)
- [Documentation du module `jena-graphrag`](../../../jena-graphrag/README.md)
- [Tests d'intégration généraux d'Apache Jena](../../../jena-integration-tests/)
- [Guide de build Apache Jena](../../../BUILD.md)
- [Modèle de menaces Apache Jena](../../../THREAT_MODEL.md)
- [Documentation Apache Jena](https://jena.apache.org/documentation/)
- [Architecture Decision Records](https://adr.github.io/)

_Dernière mise à jour : 2026-08-08_
