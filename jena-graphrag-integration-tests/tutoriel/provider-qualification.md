<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   SPDX-License-Identifier: Apache-2.0
-->

# Qualification fournisseur et diagnostic

Ce parcours qualifie le corpus PDF enrichi avec les services réels d'embeddings et de génération de réponses. Il est distinct de [Getting started](getting-started.md) car il peut effectuer de nombreux appels externes et consommer des quotas importants.

## Prérequis et état

Les paramètres doivent être locaux et valides :

```bash
make providers-bootstrap
make pdfs-check
make project-prepare
make corpus-materialize
make corpus-semantic-extract
make fuseki-start
make fuseki-ping
make graphrag-config
make indexing-start
make indexing-wait
```

`corpus-semantic-extract`, `indexing-start` et les qualifications de réponse appellent les services externes. Les fichiers `../env/.env.user` et `../env/generated/` peuvent contenir les paramètres nécessaires : ne jamais les afficher ni les ajouter à Git.

## Qualifier les réponses et citations

```bash
make chat-ask-questions
make chat-ask-modes
make chat-qualify-mode QUESTION="What is GraphRAG?" MODE=basic TOP_K=1
make chat-qualify-drift QUESTION="What is GraphRAG?" TOP_K=1
```

`chat-ask-questions` contrôle cinq questions du corpus avec des citations PDF. `chat-ask-modes` vérifie les contextes et citations des modes `basic`, `local` et `global`. `chat-qualify-mode` valide le type des ressources, l'ordre des citations et le plafond `TOP_K`; `chat-qualify-drift` vérifie le primer vectoriel de communautés ainsi que `reasonStop` et `followUpCount`.

Ces commandes acceptent une abstention déterministe uniquement lorsqu'aucun contexte correspondant n'existe. Elles échouent pour une réponse vide, des citations incompatibles ou un dépassement de limite.

## Qualifier les limites DRIFT

```bash
make chat-qualify-drift-limits QUESTION="What is GraphRAG?"
```

Cette cible arrête puis redémarre Fuseki avec les valeurs non défaut `communityTopK=1`, `maxFollowUps=1`, `contextTokenBudget=64` et `localTopK=1`, réindexe le corpus et contrôle la configuration publiée ainsi que les limites observées. Adapter les valeurs avec `DRIFT_LIMIT_COMMUNITY_TOP_K`, `DRIFT_LIMIT_MAX_FOLLOW_UPS`, `DRIFT_LIMIT_CONTEXT_TOKEN_BUDGET` et `DRIFT_LIMIT_LOCAL_TOP_K`.

Le redémarrage et la réindexation modifient l'état temporaire du tutoriel. Relancer `make fuseki-start` suivi de `make indexing-start indexing-wait` rétablit la configuration habituelle.

## Diagnostiquer et nettoyer

```bash
make fuseki-status
make context-question QUESTION="What is GraphRAG?" MODE=global
make fuseki-stop
```

`fuseki-status` et `context-question` sont des diagnostics : ils ne lancent ni indexation ni génération de réponse. En cas d'échec, consulter `target/tranche-7-tutoriel/fuseki.log`. `make fuseki-stop` conserve l'état ; `make tutorial-clean` le supprime de manière destructive.