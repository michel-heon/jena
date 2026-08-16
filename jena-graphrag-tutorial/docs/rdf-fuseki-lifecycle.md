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

# Cycle de vie RDF dans Fuseki

Ce guide exerce le cycle complet d'un petit document GraphRAG dans le dataset
Fuseki isolé du tutoriel : import, lecture, modification, suppression,
vérification et reconstruction de l'index vectoriel. Il utilise uniquement les
interfaces de production Fuseki et GraphRAG ; aucune route réservée au tutoriel
n'est ajoutée.

L'exemple écrit dans le **graphe par défaut** du dataset. C'est le bon choix
pour ce serveur de tutoriel : `GraphRAGIndexingService` lit ce graphe lorsqu'il
crée les `Document` et `Chunk` et lorsqu'il lance la vectorisation. Un graphe
nommé est utile pour une séparation applicative, mais il n'est pas indexé par ce
contrat GraphRAG actuel sans une projection explicite vers le graphe par défaut.

## Prérequis et isolement

Le parcours appelle le fournisseur d'embeddings à chaque indexation ; il peut
donc consommer du quota. Il ne demande aucune réponse de chat. Ne lancez pas ce
parcours sur un Fuseki partagé : il autorise les opérations SPARQL Update du
dataset de tutoriel et reconstruit son index local.

Depuis `jena-graphrag-tutorial/` :

```bash
make tutorial-clean
make providers-bootstrap
make project-prepare
make corpus-materialize
make fuseki-start-basic
make fuseki-ping
make corpus-load
```

Le corpus, le PID, les journaux et l'index sont alors contenus sous
`target/tutorial-state/`. Cette préparation ne modifie ni les fixtures PDF
ni les fichiers locaux de paramètres de fournisseurs.

## Parcours reproductible

La cible suivante réalise l'import, la lecture, la modification, la
suppression, les deux indexations nécessaires au constat, puis la reconstruction
du serveur et de l'index à partir du corpus initial :

```bash
make rdf-lifecycle
```

Résultats attendus :

- `RDF import verified` après l'ajout du `Document` et de son `Chunk` ;
- une tâche GraphRAG terminée après l'indexation du chunk importé ;
- `RDF update verified` après le remplacement contrôlé de `grag:text` ;
- `RDF deletion verified` après la suppression ciblée du document et du chunk ;
- `Clean tutorial state restored` après redémarrage et reconstruction de
  `target/tutorial-state/real-provider-index`.

La cible utilise les URI stables et exclusivement dédiées au tutoriel
`urn:graphrag:tutorial:rdf-lifecycle:document` et
`urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0`. Elle vérifie leur
absence avant de déclarer l'état restauré.

## Les opérations, explicitement

Fuseki expose le SPARQL Update standard sur `/{dataset}/update` et les requêtes
SPARQL sur `/{dataset}/sparql`. Pour exécuter les commandes à la main, récupérer
d'abord l'URL locale produite par le tutoriel :

```bash
base="http://127.0.0.1:$(cat target/tutorial-state/port)/graphrag-smoke"
document='urn:graphrag:tutorial:rdf-lifecycle:document'
chunk='urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0'
```

Si votre environnement redéfinit `GRAPHRAG_TUTORIAL_DATASET`, remplacez
`graphrag-smoke` par cette valeur. Vérifiez toujours l'URI et le graphe ciblés
avant de soumettre une mutation.

### Importer le document

```bash
curl --fail --silent --show-error -X POST "$base/update" \
  --data-urlencode "update=
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    INSERT DATA {
      <$document> a grag:Document ;
        grag:id \"rdf-lifecycle-document\" ;
        grag:title \"RDF lifecycle tutorial document\" .
      <$chunk> a grag:Chunk ;
        grag:id \"rdf-lifecycle-chunk-0\" ;
        grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" ;
        grag:partOf <$document> .
    }"
```

### Lire et vérifier les ressources chargées

```bash
curl --fail --silent --show-error -G "$base/sparql" \
  --data-urlencode "query=
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    SELECT ?resource ?type ?text ?parent WHERE {
      VALUES ?resource { <$document> <$chunk> }
      ?resource a ?type .
      OPTIONAL { ?resource grag:text ?text }
      OPTIONAL { ?resource grag:partOf ?parent }
    }
    ORDER BY ?resource" \
  --data-urlencode 'format=application/sparql-results+json'
```

Les deux ressources sont dans le graphe par défaut. Lancer ensuite
`make indexing-start indexing-wait` pour créer le vecteur du chunk importé.

### Modifier un chunk de façon contrôlée

La forme `DELETE`/`INSERT` ne modifie que le texte connu du chunk ciblé. Elle
ne supprime rien si la valeur attendue a déjà changé :

```bash
curl --fail --silent --show-error -X POST "$base/update" \
  --data-urlencode "update=
    PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>
    DELETE { <$chunk> grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" }
    INSERT { <$chunk> grag:text \"RDF lifecycle revised marker: the RDF graph is the source of truth.\" }
    WHERE  { <$chunk> grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" }"
```

Relancez la requête de lecture précédente et contrôlez le nouveau littéral.

## Suppression, ressources dérivées et indexation

Pour ce document minimal, seules les deux ressources ci-dessus existent : aucune
entité, relation, communauté ou finding dérivé n'est créé. La suppression peut
donc être ciblée par URI :

```bash
curl --fail --silent --show-error -X POST "$base/update" \
  --data-urlencode "update=
    DELETE { <$document> ?documentPredicate ?documentObject .
             <$chunk> ?chunkPredicate ?chunkObject }
    WHERE  { OPTIONAL { <$document> ?documentPredicate ?documentObject }
             OPTIONAL { <$chunk> ?chunkPredicate ?chunkObject } }"
```

Vérifiez ensuite que la requête suivante retourne `0` :

```sparql
SELECT (COUNT(*) AS ?count) WHERE {
  VALUES ?resource {
    <urn:graphrag:tutorial:rdf-lifecycle:document>
    <urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0>
  }
  ?resource ?predicate ?object
}
```

Pour un corpus enrichi, ne supprimez pas arbitrairement les relations et
communautés : elles peuvent relier plusieurs documents. Identifiez d'abord les
chunks, entités, relations, rapports et findings qui dépendent réellement du
document, puis régénérez l'enrichissement du corpus cohérent. Ce tutoriel
recommande de restaurer son corpus isolé plutôt que de tenter une suppression
heuristique de ressources sémantiques partagées.

### Limite actuelle : la réindexation n'invalide pas les vecteurs

Le contrat de production actuel indexe un chunk seulement lorsque son URI n'est
pas encore présente dans `VectorIndex`. Après une modification, une seconde
indexation ne remplace donc pas son vecteur ; après une suppression RDF, elle ne
retire pas non plus le vecteur orphelin. Le graphe RDF reste la source de vérité
et le contexte ignore une URI vectorielle qui n'est plus un `grag:Chunk`, mais
l'index Lucene ne devient pas propre par lui-même.

La procédure sûre est : arrêter le Fuseki du tutoriel, supprimer **uniquement**
son index temporaire, redémarrer depuis le corpus sain et indexer de nouveau.
`make rdf-lifecycle` effectue exactement cette reconstruction. Pour la réaliser
manuellement :

```bash
make fuseki-stop
rm -rf target/tutorial-state/real-provider-index
rm -f target/tutorial-state/task-id target/tutorial-state/index-response.json
make fuseki-start-basic fuseki-ping corpus-load indexing-start indexing-wait
```

Ces suppressions concernent exclusivement l'état temporaire local de ce
tutoriel. Elles sont destructives, mais les PDF versionnés et les fichiers
`env/` sont conservés. Terminez avec `make fuseki-stop`; utilisez
`make tutorial-clean` pour supprimer l'ensemble de l'état temporaire.
