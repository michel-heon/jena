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

# Indexation vectorielle et configuration

Ce parcours démarre Fuseki avec le corpus du tutoriel, contrôle sa configuration publique et lance l'indexation vectorielle des chunks. L'indexation appelle le service de génération d'embeddings configuré et consomme son quota.

## Prérequis

Préparer les paramètres locaux et un corpus. Pour une indexation de base :

```bash
make providers-bootstrap
make project-prepare
make corpus-materialize
make fuseki-start-basic
make fuseki-ping
make corpus-load
```

Utiliser `make corpus-semantic-extract`, `make fuseki-start fuseki-ping` puis `make corpus-load` pour indexer aussi les ressources d'un corpus enrichi. Le démarrage charge les paramètres des services dans le seul processus Fuseki, mais ne les appelle pas. `corpus-load` valide le Turtle avec le binaire `riot` installé avant de remplacer le graphe par défaut.

## Contrôler la configuration

```bash
make graphrag-config
make fuseki-status
```

`graphrag-config` affiche la configuration GraphRAG publique : elle doit confirmer que GraphRAG est activé et ne doit contenir ni secret ni instruction système. `fuseki-status` contrôle le processus, le ping HTTP et les routes de diagnostic sans déclencher de chat ou d'indexation.

## Lancer et suivre l'indexation

```bash
make indexing-start
make indexing-status
make indexing-wait
```

`indexing-start` appelle `POST /{dataset}/graphrag/index`, enregistre le `taskId` dans l'état temporaire et vectorise les chunks visibles. `indexing-status` affiche les compteurs retournés par l'API de tâche : chunks à traiter, vecteurs créés avec succès et pourcentage fondé sur ce travail réel. `indexing-wait` s'arrête à l'état `done`; tout autre état terminal est une erreur et les détails sont dans `target/tutorial-state/fuseki.log`.

## Vérifier et arrêter

```bash
make corpus-verify-pdfs
make fuseki-stop
```

La vérification confirme le nombre de documents PDF chargés dans Fuseki. L'arrêt conserve corpus, index et journaux ; `make tutorial-clean` les supprime de manière destructive.
