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

# Modes de récupération et de réponse

GraphRAG fournit les modes `basic`, `local`, `global` et `drift`. Les requêtes de contexte peuvent appeler le service de génération d'embeddings ; les requêtes de réponse appellent aussi le service de génération de réponses et consomment donc leurs quotas.

## Prérequis communs

Les quatre modes exigent Fuseki actif et une indexation terminée. Les modes `local`, `global` et `drift` exigent en plus un corpus enrichi :

```bash
make providers-bootstrap
make project-prepare
make corpus-materialize
make corpus-semantic-extract
make fuseki-start
make fuseki-ping
make corpus-load
make indexing-start
make indexing-wait
```

`corpus-semantic-extract` et l'indexation appellent des services externes. Les commandes suivantes supposent cet état temporaire ; `make tutorial-clean` le supprime.

## Basic, local et global

```bash
make context-question QUESTION="What is GraphRAG?" MODE=basic
make context-question QUESTION="What is GraphRAG?" MODE=local
make context-question QUESTION="What is GraphRAG?" MODE=global
```

`context-question` affiche le contexte RDF brut et les compteurs de ressources sans appeler le service de génération de réponses. `basic` recherche les chunks vectorisés. `local` requiert les entités et relations extraites. `global` requiert des communautés avec `grag:summary` ou `grag:fullContent`.

Pour obtenir une réponse citée :

```bash
make chat-question QUESTION="What is GraphRAG?" MODE=basic
make chat-question QUESTION="What is GraphRAG?" MODE=local
make chat-question QUESTION="What is GraphRAG?" MODE=global
```

La réponse doit contenir les citations des ressources retournées par le contexte. En l'absence de résultat correspondant, l'endpoint renvoie une abstention déterministe sans appeler le service de génération de réponses.

## DRIFT

```bash
make chat-qualify-drift QUESTION="What is GraphRAG?" TOP_K=1
```

DRIFT utilise un primer limité à des rapports de communautés obtenus par recherche vectorielle. Il requiert l'index vectoriel dédié aux communautés, alimenté avec `grag:summary` ou `grag:fullContent`; aucun repli lexical, texte ou graphe ne le remplace. Sans cet index, l'endpoint signale explicitement une erreur de configuration.

La commande vérifie le plafond `TOP_K`, les citations du primer, `reasonStop` et `followUpCount`. Les suites locales supplémentaires de la traversée peuvent ajouter des citations. Elle appelle les services d'embeddings et de génération de réponses.

Les limites runtime par défaut sont `communityTopK=5`, `maxFollowUps=3`, `contextTokenBudget=4096` et `localTopK=1`. Elles sont vérifiées avec des valeurs non défaut dans le guide de [qualification fournisseur](provider-qualification.md).