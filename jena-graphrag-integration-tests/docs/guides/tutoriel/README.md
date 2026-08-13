<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information regarding
   copyright ownership. The ASF licenses this file to You under the
   Apache License, Version 2.0 (the "License"); you may not use
   this file except in compliance with the License. You may obtain
   a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied. See the License for the
   specific language governing permissions and limitations
   under the License.

   SPDX-License-Identifier: Apache-2.0
-->

# Tutoriel Tranche 7: corpus PDF GraphRAG

Ce tutoriel exécute la qualification ultime du corpus PDF GraphRAG, action par action. Son [Makefile](Makefile) encapsule les commandes, conserve l'état de travail sous `target/tranche-7-tutoriel/` et ne stocke aucune valeur de fournisseur.

La procédure réutilise `DocumentIngestionService`, `GraphRAGFusekiUIServer` et les routes GraphRAG de production, conformément à ADR-608. Les appels aux fournisseurs d'embeddings et de chat sont réels et consomment des quotas. Le profil local utilise deux petits PDF pour accélérer le développement; le profil complet de qualification utilise les douze PDF configurés dans le modèle d'environnement.

## Préparation

Depuis la racine du dépôt, ouvrir un terminal dans ce répertoire:

```bash
cd jena-graphrag-integration-tests/docs/guides/tutoriel
make help
```

Le prérequis est Java, Maven, Node.js, `curl` et `make`.

## Étape 1: préparer les fournisseurs

```bash
make providers-bootstrap
```

La cible crée la projection ignorée `env/generated/real-providers.env.sh`. Si nécessaire, renseigner `jena-graphrag-integration-tests/env/.env.user`, puis relancer `make providers-bootstrap`. Ne jamais afficher, committer ou transmettre les valeurs de ce fichier.

## Étape 2: vérifier les PDF

```bash
make pdfs-check
```

Résultat attendu: le nombre de PDF configuré dans `GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT`. Le profil local de développement utilise trois PDF dans `pdf-development`, dont la référence GraphRAG local-to-global; le modèle d'environnement conserve le corpus complet de douze PDF.

## Étape 3: compiler et construire le classpath

```bash
make project-prepare
```

Cette action compile le module d'intégration et écrit le classpath de test temporaire. Elle ne contacte aucun fournisseur.

## Étape 4: ingérer les PDF

```bash
make corpus-materialize
```

Cette étape lance `PdfCorpusMaterializer`, qui découvre les PDF configurés et délègue l'ingestion à `DocumentIngestionService`. Le fichier Turtle temporaire est écrit sous le chemin configuré par `GRAPHRAG_TUTORIAL_CORPUS_PATH`. Les avertissements PDFBox de polices ou tables Unicode embarquées sont acceptables.

## Étape 5: extraire le graphe sémantique

```bash
make corpus-semantic-extract
```

Cette étape appelle les extracteurs HTTP de production pour créer les entités, relations et communautés à partir des chunks PDF. Les ressources extraites et les réponses fournisseur réussies sont sauvegardées progressivement: après une interruption, le Turtle contient le graphe partiel et une relance ne rappelle que les chunks ou communautés restants. Le point de contrôle est conservé lorsque le corpus est rematérialisé et est invalidé automatiquement si le contenu des chunks change. Ces appels sont réels et consomment des quotas.

## Étape 6: démarrer Fuseki

```bash
make fuseki-start
make fuseki-ping
make fuseki-status
make fuseki-open
```

`make fuseki-start` charge les valeurs des fournisseurs uniquement dans le processus Fuseki, fixe l'instruction système du corpus et démarre le serveur en arrière-plan. La cible affiche l'URL locale et le fichier journal. `make fuseki-ping` doit retourner avec le code `0`.

`make fuseki-status` affiche le PID et l'URL configurés, puis l'état du processus, du ping HTTP et des API GraphRAG sûres à sonder: `config`, `context` et `status` lorsqu'une tâche d'indexation est connue. Les routes `answer` et `index` sont explicitement indiquées comme non sondées, car elles appelleraient un fournisseur de chat ou déclencheraient une indexation. La commande reste purement diagnostique.

`make fuseki-open` lance l'adresse IP de WSL dans le navigateur par défaut de Windows. Cela fonctionne lorsque le relais Windows vers `127.0.0.1` est désactivé. Ne copiez pas les variables de fournisseur depuis l'environnement du processus.

## Étape 7: contrôler la configuration publique

```bash
make graphrag-config
```

Le JSON indique que GraphRAG est activé. Il ne doit contenir ni instruction système ni secret de fournisseur.

## Étape 8: lancer l'indexation vectorielle

```bash
make indexing-start
make indexing-status
```

L'indexation utilise `POST /{dataset}/graphrag/index`, une route publique existante. Elle ajoute un document de déclenchement, puis vectorise tous les chunks visibles, y compris ceux du corpus PDF. `make indexing-start` conserve le `taskId` dans l'état temporaire et affiche la réponse. `make indexing-status` affiche l'état, les nombres de documents, de PDF et de chunks à vectoriser. Une fois la tâche terminée, il confirme le nombre de chunks indexés et `100%`. Pendant l'exécution, l'API GraphRAG ne fournit pas de compteur par chunk: le pourcentage intermédiaire est donc explicitement indiqué comme indisponible.

## Étape 9: attendre la fin de la tâche

```bash
make indexing-wait
```

La cible s'arrête lorsque l'état devient `done`. Tout autre état terminal est un échec et le journal Fuseki est disponible sous `target/tranche-7-tutoriel/fuseki.log`.

## Étape 10: vérifier le corpus ingéré

```bash
make corpus-verify-pdfs
```

Résultat attendu: le nombre de PDF configuré dans `GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT`. Le document de déclenchement est distinct et n'est pas compté car sa source ne termine pas par `.pdf`.

Pour afficher les ressources et liens GraphRAG créés dans le dataset:

```bash
make corpus-statistic
```

La cible est en lecture seule. Elle applique la requête SPARQL directement au fichier Turtle enrichi, sans démarrer Fuseki. Elle affiche les triplets, documents, PDF, chunks, entités, relations, communautés, findings et les liens `partOf`, `hasEntity`, `relatedTo` et `inCommunity`.

Pour parcourir l'intégralité du graphe RDF local, page par page:

```bash
make graph-cat
```

Cette cible sérialise le corpus Turtle avec la commande RIOT de Jena, puis l'affiche avec `more`. Elle est également en lecture seule et ne nécessite pas Fuseki.

## Étape 11: poser les cinq questions

```bash
make chat-ask-questions
```

La cible interroge successivement:

1. What is GraphRAG?
2. What is the difference between local and global GraphRAG?
3. How can an RDF knowledge graph support GraphRAG?
4. What is KG2RAG?
5. How is SPARQL generated from natural language over federated knowledge graphs?

Pour chaque réponse, elle vérifie une réponse non vide et une citation dont l'URI commence par `http://ormynet.com/ns/data#chunk-`. La formulation exacte n'est pas vérifiée, car elle dépend du fournisseur.

Pour poser une question libre avec la recherche hybride par défaut et les mêmes contrôles:

```bash
make chat-question QUESTION="What is the role of embeddings in GraphRAG?"
```

`chat-question` peut aussi sélectionner le contexte fourni au chat avec `MODE=basic`, `MODE=local` ou `MODE=global`:

```bash
make chat-question QUESTION="What is GraphRAG?" MODE=local
```

Le mode est transmis à l'endpoint de production `/graphrag/answer`; le fournisseur de chat répond à partir du contexte sélectionné et les citations correspondent aux ressources récupérées (chunks pour `basic`, relations pour `local`, communautés pour `global`). Lorsqu'aucun contexte ne correspond, l'endpoint retourne une abstention déterministe sans appeler le fournisseur; `chat-question` l'affiche sans erreur. Sans `MODE`, la recherche hybride et le contrôle des citations PDF sont préservés.

Pour qualifier les trois modes avec les fournisseurs réels configurés, exécuter:

```bash
make chat-ask-modes
```

Pour qualifier un contexte et sa réponse dans le tutoriel, après `indexing-wait`, exécuter:

```bash
make chat-qualify-mode QUESTION="What is GraphRAG?" MODE=basic TOP_K=1
```

`QUESTION`, `MODE=basic|local|global|drift` et `TOP_K=1..100` sont des options. Sans argument, la cible utilise `What is GraphRAG?`, `basic` et `1`. Elle compare systématiquement les citations de `/graphrag/answer` aux ressources et passages de `/graphrag/context`, vérifie le type de ressource propre au mode et le plafond `TOP_K`, ou accepte uniquement l'abstention déterministe sans contexte ni citation. Avec `MODE=basic` et un serveur tutoriel indexé, le contexte provient de la recherche vectorielle. `make chat-qualify-basic-vector` reste un alias compatible de la qualification `basic` par défaut.

### Test spécifique DRIFT

Après `make corpus-semantic-extract`, `make fuseki-start` et `make indexing-wait`, exécuter la qualification DRIFT avec les fournisseurs réels configurés :

```bash
make chat-qualify-drift QUESTION="What is GraphRAG?" TOP_K=1
```

Cette qualification vérifie que le primer de `/graphrag/context?mode=drift` ne contient que des rapports de communautés vectorisés, dans l'ordre de leur recherche et dans la limite `TOP_K`. Elle appelle ensuite `/graphrag/answer?mode=drift` et vérifie que les citations commencent par celles du primer, que `reasonStop` est renseigné et que `followUpCount` est un entier positif ou nul. Les preuves locales supplémentaires d'une itération DRIFT peuvent compléter les citations du primer.

Le mode DRIFT exige un index vectoriel de communautés contenant `mg:summary` ou `mg:fullContent`. En son absence, l'endpoint répond explicitement une erreur de configuration : le test ne doit pas être remplacé par les modes `basic`, `local` ou `global`. Le contexte DRIFT n'appelle pas de chat ni de LLM de génération ; il appelle le fournisseur d'embeddings configuré afin de rechercher le primer vectoriel. La commande consomme donc les quotas d'embeddings et de chat.

La qualification des limites runtime redémarre Fuseki avec des valeurs DRIFT non défaut, réindexe le corpus, puis contrôle la configuration publiée et les bornes effectivement observées :

```bash
make chat-qualify-drift-limits QUESTION="What is GraphRAG?"
```

Les valeurs par défaut de cette cible sont `communityTopK=1`, `maxFollowUps=1`, `contextTokenBudget=64` et `localTopK=1`. Elles peuvent être ajustées avec les variables `DRIFT_LIMIT_COMMUNITY_TOP_K`, `DRIFT_LIMIT_MAX_FOLLOW_UPS`, `DRIFT_LIMIT_CONTEXT_TOKEN_BUDGET` et `DRIFT_LIMIT_LOCAL_TOP_K`.

La commande pose la même question aux trois modes, `What is GraphRAG?` par défaut, afin de comparer directement leurs contextes et réponses. Elle est configurable avec `make chat-ask-modes CHAT_MODE_QUESTION="..."`. Elle compare les citations de `/graphrag/answer` aux ressources et passages de `/graphrag/context`. Lorsqu'un contexte existe, elle vérifie aussi les types attendus : chunks pour `basic`, ressources mixtes GraphRAG pour `local`, et communautés pour `global`. Elle rejette une réponse qui présente GraphDB, Elasticsearch, LM Studio ou LangChain comme des composants génériques de Microsoft GraphRAG, ou qui ne les rattache pas au corpus ou à l'intégration RDF décrite. Elle affiche une réponse citée lorsque le contexte existe, ou une abstention conforme lorsque le mode ne trouve aucune ressource.

Le corpus de développement est volontairement limité à trois PDF, dont deux sur une intégration RDF de GraphRAG et une référence GraphRAG local-to-global. Avec un index GraphRAG configuré, `basic` interroge les chunks top-k du vecteur; sans index configuré, il conserve le repli texte/littéral pour l'inspection RDF. Pour traiter l'absence de contexte local mixte ou de plusieurs rapports `global` comme des échecs, lancer `make chat-ask-modes CHAT_MODE_REQUIRE_MIXED_LOCAL=true CHAT_MODE_REQUIRE_MULTIPLE_GLOBAL=true`. La qualification vérifie ainsi la traçabilité et les types sans rejeter une réponse qui reprend les composants RDF lorsque les citations les étayent. Cette commande effectue des appels réels au fournisseur de chat et consomme donc du quota.

`context-question` reste une commande de diagnostic: elle interroge `/graphrag/context`, affiche le contexte RDF brut et n'appelle aucun fournisseur ni LLM. Pour inspecter directement ce contexte avec un mode donné, utiliser l'une des valeurs `basic`, `local` ou `global`:

```bash
make context-question QUESTION="What is GraphRAG?" MODE=local
```

L'extraction sémantique de l'étape 5 crée les entités, relations et communautés nécessaires aux modes `local` et `global`. Lorsqu'il n'y a aucun résultat, la cible affiche les compteurs correspondants et la précondition du mode. Avec l'index GraphRAG configuré, `basic` exige des chunks vectoriels; sinon, la cible utilise l'index texte ou une correspondance littérale.

Pour inspecter les communautés disponibles pour le mode `global`:

```bash
make communities-list
```

Les quatre commandes d'inspection suivantes lisent directement le Turtle local avec ARQ; Fuseki n'a pas besoin d'être démarré:

```bash
make entities-list
make relationships-list
make communities-list
make findings-list
```

Elles affichent respectivement les entités, relations, communautés et constats extraits. Elles permettent aussi de suivre le graphe partiel après une interruption de l'étape 5.

## Étape 12: arrêter et nettoyer

```bash
make fuseki-stop
make tutorial-clean
```

`make fuseki-stop` arrête le Fuseki du tutoriel. `make tutorial-clean` supprime l'état temporaire, dont le corpus Turtle, l'index et le journal.

## Parcours complet

Une fois les étapes comprises, cette commande les enchaîne:

```bash
make tutorial-run
```

Le parcours automatisé Playwright de référence reste disponible depuis la racine du dépôt avec `make graphrag-integration-ultimate-pdf-corpus-smoke`.
