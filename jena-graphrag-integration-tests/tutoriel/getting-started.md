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

# Getting started GraphRAG

Ce parcours obtient une première réponse GraphRAG citée à partir du corpus PDF local : préparer l'espace de travail, indexer, puis interroger.

Il utilise les services de production de Jena : `DocumentIngestionService`, `GraphRAGFusekiUIServer` et les routes HTTP GraphRAG. Aucun endpoint réservé au tutoriel n'est créé.

## Prérequis

Java, Maven, Node.js, `curl` et `make` doivent être disponibles. Le parcours appelle un service de génération d'embeddings pour l'indexation et un service de génération de réponses pour la question : il requiert leurs paramètres locaux et consomme leurs quotas. Les fichiers `.env` et `.env.user` restent locaux ; ne les affichez ni ne les commitez.

Depuis la racine du dépôt :

```bash
cd jena-graphrag-integration-tests/tutoriel
```

Cette commande place le terminal dans le répertoire qui contient le `Makefile` du tutoriel.

```bash
make providers-bootstrap
```

Cette commande crée les fichiers locaux de projection des paramètres des services d'embeddings et de génération de réponses sous `../env/generated/`. Renseignez `../env/.env.user` si cette projection ne contient pas ces paramètres, puis relancez la commande. Elle ne lance ni Fuseki ni appel à ces services.

## Exécuter le premier parcours

Assurez-vous que le serveur du tutoriel n'est pas déjà en cours d'exécution. Pour repartir d'un état temporaire vide :

```bash
make tutorial-clean
```

Cette commande arrête le Fuseki du tutoriel s'il est actif et efface le corpus temporaire, l'index et les journaux. Elle ne modifie pas les PDF de référence ni les fichiers de configuration locaux.

### 1. Vérifier le corpus PDF

```bash
make pdfs-check
```

Cette commande vérifie que le profil local contient le nombre attendu de PDF. Avec la configuration de développement, le résultat attendu est `3 PDF fixture(s) found.` Elle n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 2. Préparer le classpath

```bash
make project-prepare
```

Cette commande compile les dépendances du tutoriel et écrit un classpath temporaire sous `../target/tranche-7-tutoriel/`. Elle ne démarre pas Fuseki et n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 3. Ingérer les PDF

```bash
make corpus-materialize
```

Cette commande transforme les PDF locaux en ressources RDF `Document` et `Chunk` au moyen de `DocumentIngestionService`. Le corpus Turtle temporaire est écrit sous `../target/tranche-7-tutoriel/`. Elle n'extrait pas encore les entités, relations ni communautés, et n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 4. Démarrer Fuseki

```bash
make fuseki-start-basic
```

Cette commande démarre le serveur Fuseki de production avec les documents et chunks du corpus. Elle charge les paramètres des services d'embeddings et de génération de réponses uniquement dans le processus du serveur, mais ne les appelle pas au démarrage. L'URL et le fichier journal sont affichés.

### 5. Vérifier que le serveur répond

```bash
make fuseki-ping
```

Cette commande appelle `/$/ping` jusqu'à ce que Fuseki soit disponible. Elle se termine avec une erreur si le serveur ne répond pas dans le délai prévu.

### 6. Inspecter la configuration publique

```bash
make graphrag-config
```

Cette commande affiche la configuration GraphRAG publiée par Fuseki. Vérifiez que GraphRAG est activé ; les secrets et l'instruction système ne doivent pas apparaître dans ce JSON.

### 7. Déclencher l'indexation vectorielle

```bash
make indexing-start
```

Cette commande appelle `POST /{dataset}/graphrag/index`, enregistre le `taskId` dans l'état temporaire et lance la vectorisation des chunks. Elle appelle le service de génération d'embeddings configuré et consomme son quota.

### 8. Attendre la fin de l'indexation

```bash
make indexing-wait
```

Cette commande consulte l'état de la tâche jusqu'à `done`. Un état `failed` interrompt le parcours et la cause détaillée est disponible dans le journal Fuseki.

### 9. Poser une première question

```bash
make chat-question QUESTION="What is GraphRAG?" MODE=basic
```

Cette commande appelle `/graphrag/answer` avec `mode=basic`, puis affiche la réponse et les citations de chunks. Elle appelle le service de génération de réponses configuré et consomme son quota. Le résultat attendu est une réponse non vide et au moins une citation de chunk du corpus.

Pour choisir la question :

```bash
make chat-question QUESTION="What is the role of embeddings in GraphRAG?" MODE=basic
```

Cette commande remplace uniquement le texte de la question de l'étape 9. L'indexation doit déjà être terminée.

`mode=basic` est volontairement le seul mode de ce parcours. Les modes `local`, `global` et `drift` nécessitent l'enrichissement sémantique et, pour DRIFT, l'index vectoriel dédié aux rapports de communautés. Ils sont traités dans le [parcours détaillé](README.md).

## Inspecter et arrêter

Pendant que Fuseki est démarré :

```bash
make fuseki-status
```

Cette commande affiche le PID, l'URL et la disponibilité des routes GraphRAG sûres à sonder. Elle n'appelle ni le chat ni l'indexation.

```bash
make graphrag-config
```

Cette commande réaffiche la configuration publique sans exposer de secret ni d'instruction système.

À la fin :

```bash
make fuseki-stop
```

Cette commande arrête uniquement le processus Fuseki ; elle conserve le corpus temporaire, l'index et les journaux. Utilisez `make tutorial-clean` pour les supprimer également.