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

# Inspection du graphe RDF

Ce guide inspecte le Turtle produit par le tutoriel. Toutes les commandes exécutent ARQ localement : Fuseki n'a pas besoin d'être démarré, aucun service externe n'est appelé et aucun quota n'est consommé.

## Prérequis

Depuis `jena-graphrag-tutorial/`, matérialiser un corpus :

```bash
make project-prepare
make corpus-materialize
```

Pour inspecter entités, relations, communautés et findings, exécuter aussi `make corpus-semantic-extract`. Cette dernière étape appelle des services externes et modifie le Turtle local.

## Vue d'ensemble

```bash
make corpus-statistic
make graph-cat
```

`corpus-statistic` compte les triplets et les ressources `Document`, `Chunk`, `Entity`, `Relationship`, `Community` et `Finding`, ainsi que leurs liens. `graph-cat` sérialise le Turtle avec RIOT et l'affiche page par page. Ces commandes sont en lecture seule ; quitter l'affichage avec `q` si nécessaire.

## Ressources sémantiques

```bash
make entities-list
make relationships-list
make communities-list
make findings-list
```

Ces cibles affichent les URI et les propriétés utiles des ressources extraites. Sans enrichissement, elles affichent un message indiquant l'absence de résultats ; cela ne signale pas une erreur d'ingestion des documents et chunks.

Les liens structurels ont le sens suivant : `grag:partOf` relie un chunk à son document, `grag:hasEntity` associe un chunk à une entité, `grag:relatedTo` relie des entités, et `grag:inCommunity` rattache une entité à une communauté. Vérifier ces nombres avec `corpus-statistic` avant d'utiliser les modes avancés.

## Réinitialiser l'état

```bash
make tutorial-clean
```

Cette commande est destructive pour le corpus, l'index et les journaux temporaires du tutoriel. Elle n'agit ni sur les fixtures PDF ni sur les fichiers d'environnement.