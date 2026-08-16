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

# Compiler Jena et démarrer avec GraphRAG

Ce parcours commence par compiler ce dépôt Apache Jena, puis installe Jena CLI, Fuseki et l'artefact GraphRAG résultants dans un répertoire choisi dans `env/.env`. Il obtient ensuite une première réponse GraphRAG citée à partir du corpus PDF local : préparer l'espace de travail, indexer, puis interroger. Voir aussi le [README du tutoriel](../README.md) pour le parcours complet.

Il utilise les services de production de Jena : `DocumentIngestionService`, le lanceur `fuseki-server` installé et les routes HTTP GraphRAG. Aucun endpoint réservé au tutoriel n'est créé.

## Prérequis

Jena 6 requiert Java 21 ou une version ultérieure. Java, Maven, Node.js, `curl`, `make` et `tar` doivent être disponibles. Le parcours appelle un service de génération d'embeddings pour l'indexation et un service de génération de réponses pour la question : il requiert leurs paramètres locaux et consomme leurs quotas. Les fichiers `.env` et `.env.user` restent locaux ; ne les affichez ni ne les commitez.

La compilation du dépôt produit et installe :

- `apache-jena` : les API courantes, ARQ/SPARQL, TDB et les outils CLI tels que `riot`, `sparql` et `tdb2.*` ;
- `apache-jena-fuseki` : le serveur SPARQL Fuseki autonome et son interface web ;
- le JAR expérimental `jena-graphrag` et ses dépendances d'exécution, placés sous `JENA_INSTALL_DIR/graphrag/`.

## Compiler et installer Apache Jena

### 1. Définir le répertoire d'installation dans `.env`

Depuis la racine du dépôt :

```bash
cd jena-graphrag-tutorial
```

Créez le profil local s'il n'existe pas encore :

```bash
test -f env/.env || cp env/.env.example env/.env
chmod 600 env/.env
```

Dans `env/.env`, renseignez un chemin absolu et accessible en écriture, sans guillemets ni espace autour du signe `=` :

```dotenv
JENA_INSTALL_DIR=/chemin/absolu/vers/jena-install
```

Le tutoriel lit la version de référence dans le `pom.xml` racine du dépôt : ne la recopiez pas dans ce fichier. Toutes les distributions compilées seront installées sous `JENA_INSTALL_DIR` ; aucune écriture dans `/usr` ou `/opt` et aucun privilège administrateur ne sont nécessaires.

### 2. Compiler la version de référence et l'installer

Lancez la cible d'installation du tutoriel :

```bash
make jena-install
```

La cible prépare le profil local, lit la version de référence dans le `pom.xml` racine, compile le dépôt avec les profils `complete` et `graphrag`, puis extrait les distributions dans `JENA_INSTALL_DIR`. Elle copie également le JAR GraphRAG dans `JENA_INSTALL_DIR/graphrag/`. Si l'installation existe déjà, la cible s'arrête avant toute écriture : choisissez un autre `JENA_INSTALL_DIR` ou supprimez explicitement l'installation à remplacer.

### 3. Activer et vérifier l'installation

Vérifiez les outils CLI, Fuseki et le JAR GraphRAG installés :

```bash
make jena-install-check
```

La cible utilise `JENA_INSTALL_DIR` et la version Maven du checkout pour retrouver les distributions. Elle vérifie `sparql`, `riot`, Fuseki, le JAR GraphRAG et ses dépendances, sans modifier les variables d'environnement du terminal.

Le tutoriel copie ces JARs dans son `FUSEKI_BASE/extra`, génère une configuration de service déterministe (dataset TDB2 persistant, port et index), puis démarre exclusivement `fuseki-server` depuis `JENA_INSTALL_DIR`. Il ne réutilise pas de classpath Maven pour le serveur.

## Poursuivre avec le tutoriel GraphRAG

Le terminal doit toujours se trouver dans `jena-graphrag-tutorial/`, le répertoire qui contient le `Makefile` du tutoriel.

### Préparer la configuration locale

```bash
make providers-bootstrap
```

Cette commande crée les fichiers locaux de projection des paramètres des services d'embeddings et de génération de réponses sous `env/generated/`. Renseignez `env/.env.user` si cette projection ne contient pas ces paramètres, puis relancez la commande. Elle ne lance ni Fuseki ni appel à ces services.

## Exécuter le premier parcours

Assurez-vous que le serveur du tutoriel n'est pas déjà en cours d'exécution. Pour repartir d'un état temporaire vide :

```bash
make tutorial-clean
```

Cette commande arrête le Fuseki du tutoriel s'il est actif et efface le corpus temporaire, le dataset TDB2, l'index et les journaux. Elle ne modifie pas les PDF de référence ni les fichiers de configuration locaux.

### 1. Vérifier le corpus PDF

```bash
make pdfs-check
```

Cette commande vérifie que le profil local contient le nombre attendu de PDF. Avec la configuration de développement, le résultat attendu est `3 PDF fixture(s) found.` Elle n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 2. Préparer le classpath

```bash
make project-prepare
```

Cette commande compile les dépendances du tutoriel et écrit un classpath temporaire sous `target/tutorial-state/`. Elle ne démarre pas Fuseki et n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 3. Ingérer les PDF

```bash
make corpus-materialize
```

Cette commande transforme les PDF locaux en ressources RDF `Document` et `Chunk` au moyen de `DocumentIngestionService`. Le corpus Turtle est écrit sous `data/pdf-corpus.ttl`, puis validé par le binaire `riot` installé. Elle n'extrait pas encore les entités, relations ni communautés, et n'appelle ni le service d'embeddings ni le service de génération de réponses.

### 4. Démarrer Fuseki

```bash
make fuseki-start-basic
```

Cette commande met en scène l'extension GraphRAG et toutes ses dépendances sous `target/tutorial-state/fuseki-distribution/extra`, génère `service.ttl`, puis démarre le `fuseki-server` installé avec un dataset TDB2 persistant. Une fois le ping disponible, elle envoie le corpus Turtle au endpoint Graph Store `/data?default` de Fuseki : le serveur ne lit donc pas le corpus depuis le checkout au démarrage. Elle charge les paramètres des services d'embeddings et de génération de réponses uniquement dans le processus du serveur, mais ne les appelle pas au démarrage. L'URL et le fichier journal sont affichés.

Le dataset persiste dans `target/tutorial-state/fuseki-distribution/databases/<dataset>` entre deux `make fuseki-stop` et redémarrages. `make tutorial-clean` le supprime explicitement. Pour remplacer le corpus d'un serveur déjà démarré, exécutez `make corpus-load` : la requête HTTP `PUT` remplace le graphe par défaut.

### 5. Vérifier que le serveur répond

```bash
make fuseki-ping
```

Cette commande appelle `/$/ping` jusqu'à ce que Fuseki soit disponible. Elle se termine avec une erreur si le serveur ne répond pas dans le délai prévu.

### 6. Vérifier le corpus chargé

```bash
make corpus-verify-pdfs
```

Cette commande appelle le binaire `sparql` installé, qui interroge le service Fuseki et vérifie exactement le nombre attendu de documents PDF ainsi qu'au moins un chunk. Elle échoue si l'un de ces contrôles ne correspond pas.

### 7. Inspecter la configuration publique

```bash
make graphrag-config
```

Cette commande affiche la configuration GraphRAG publiée par Fuseki. Vérifiez que GraphRAG est activé ; les secrets et l'instruction système ne doivent pas apparaître dans ce JSON.

### 8. Déclencher l'indexation vectorielle

```bash
make indexing-start
```

Cette commande appelle `POST /{dataset}/graphrag/index`, enregistre le `taskId` dans l'état temporaire et lance la vectorisation des chunks. Elle appelle le service de génération d'embeddings configuré et consomme son quota.

### 9. Attendre la fin de l'indexation

```bash
make indexing-wait
```

Cette commande consulte l'état de la tâche jusqu'à `done`. Un état `failed` interrompt le parcours et la cause détaillée est disponible dans le journal Fuseki.

### 10. Poser une première question

```bash
make chat-question QUESTION="What is GraphRAG?" MODE=basic
```

Cette commande appelle `/graphrag/answer` avec `mode=basic`, puis affiche la réponse et les citations de chunks. Elle appelle le service de génération de réponses configuré et consomme son quota. Le résultat attendu est une réponse non vide et au moins une citation de chunk du corpus.

Pour choisir la question :

```bash
make chat-question QUESTION="What is the role of embeddings in GraphRAG?" MODE=basic
```

Cette commande remplace uniquement le texte de la question de l'étape 9. L'indexation doit déjà être terminée.

`mode=basic` est volontairement le seul mode de ce parcours. Les modes `local`, `global` et `drift` nécessitent l'enrichissement sémantique et, pour DRIFT, l'index vectoriel dédié aux rapports de communautés. Ils sont traités dans le [parcours détaillé](../README.md).

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

Cette commande arrête uniquement le processus Fuseki ; elle conserve le corpus temporaire, le dataset TDB2, l'index et les journaux. Utilisez `make tutorial-clean` pour les supprimer également.
