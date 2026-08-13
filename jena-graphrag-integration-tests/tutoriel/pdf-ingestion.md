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

# Ingestion et enrichissement du corpus PDF

Ce parcours transforme les PDF de développement en documents et chunks RDF, puis extrait les entités, relations, communautés et findings nécessaires aux modes `local`, `global` et `drift`.

## Prérequis

Depuis `jena-graphrag-integration-tests/tutoriel/`, Java, Maven, Node.js, `curl` et `make` doivent être disponibles. Exécuter `make providers-bootstrap` avant l'enrichissement : `corpus-materialize` est local, mais `corpus-semantic-extract` appelle les services de génération d'embeddings et de réponses configurés et consomme leurs quotas.

Pour repartir sans corpus temporaire :

```bash
make tutorial-clean
make pdfs-check
make project-prepare
```

Le contrôle des PDF et la compilation n'appellent aucun service externe. Avec le profil de développement, `pdfs-check` affiche `3 PDF fixture(s) found.`

## Matérialiser les documents et chunks

```bash
make corpus-materialize
```

Cette commande délègue aux services de production et écrit un corpus Turtle temporaire. Elle crée les ressources `grag:Document` et `grag:Chunk`, mais aucune entité, relation, communauté ou finding. Des avertissements PDFBox concernant les polices ou tables Unicode sont acceptables.

## Enrichir le graphe

```bash
make corpus-semantic-extract
make corpus-statistic
```

L'extraction envoie les chunks aux extracteurs HTTP de production. Les résultats sont enregistrés progressivement dans le Turtle : une interruption conserve le graphe partiel et une reprise ne contacte que les chunks ou communautés restants. Une rematérialisation conserve ce point de contrôle, qui est invalidé si le contenu d'un chunk change.

`corpus-statistic` lit uniquement le Turtle local et affiche les documents, chunks, entités, relations, communautés, findings et liens structurels. Un nombre non nul de ressources sémantiques confirme que les modes avancés ont leur précondition de données.

## État et nettoyage

Le corpus enrichi est local au tutoriel et sera chargé par `make fuseki-start`. Pour supprimer ce corpus, son index et les journaux associés :

```bash
make tutorial-clean
```

Cette opération efface l'état temporaire, sans modifier les PDF de référence ni les paramètres locaux des services.