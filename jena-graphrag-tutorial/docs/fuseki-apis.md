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

# API Fuseki du tutoriel

Cette référence décrit les routes HTTP appelées par le tutoriel. Toutes les
URL partent de `FUSEKI_URL`, défini dans `env/.env`, sans barre finale. Le nom
du dataset vient de `GRAPHRAG_TUTORIAL_DATASET`.

```bash
set -a
. env/generated/real-providers.env.sh
set +a
dataset_url="$FUSEKI_URL/$GRAPHRAG_TUTORIAL_DATASET"
```

Pour une instance distante, `FUSEKI_URL` identifie un Fuseki déjà administré.
Les commandes du tutoriel modifient le dataset configuré : vérifiez toujours
`dataset_url` avant une requête d'écriture.

## Services Fuseki standards

| Usage | Méthode et URL | Effet |
| --- | --- | --- |
| Disponibilité du serveur | `GET $FUSEKI_URL/$/ping` | Vérifie que Fuseki répond. |
| Requête SPARQL | `GET` ou `POST $dataset_url/sparql` | Lit le dataset avec une requête SPARQL. |
| Mise à jour SPARQL | `POST $dataset_url/update` | Applique une mutation SPARQL Update. |
| Graphe par défaut | `PUT $dataset_url/data?default` | Remplace le graphe par défaut avec le contenu RDF envoyé. |
| Graphe nommé | `GET`, `PUT`, `POST` ou `DELETE $dataset_url/data?graph=<IRI>` | Lit ou modifie un graphe nommé via le Graph Store Protocol. |

`make corpus-load` appelle la quatrième route avec `PUT`,
`Content-Type: text/turtle` et le corpus local. C'est une opération de
remplacement : elle n'ajoute pas les triplets au graphe par défaut existant.

Les exemples suivants utilisent des paramètres encodés par `curl`, plutôt que
de concaténer directement une requête dans une URL :

```bash
curl --fail --silent --show-error -G "$dataset_url/sparql" \
  --data-urlencode 'query=SELECT (COUNT(*) AS ?triples) WHERE { ?s ?p ?o }' \
  --data-urlencode 'format=application/sparql-results+json'

curl --fail --silent --show-error -X POST "$dataset_url/update" \
  --data-urlencode 'update=INSERT DATA { <urn:example:resource> <urn:example:predicate> "value" }'
```

La configuration d'un service Fuseki peut exposer des noms de routes différents
de `sparql`, `update` et `data`. L'instance distante doit donc fournir ces
services sous ces noms pour être compatible avec le Makefile du tutoriel.

## Routes GraphRAG

GraphRAG est une extension installée dans le Fuseki local géré par le tutoriel
ou préinstallée sur une instance distante. Ces routes ne font pas partie de
l'API standard Fuseki.

| Usage | Méthode et URL | Effet |
| --- | --- | --- |
| Configuration publique | `GET $dataset_url/graphrag/config` | Lit les capacités GraphRAG sans révéler les secrets. |
| Recherche hybride | `GET` ou `POST $dataset_url/graphrag/search` | Retourne les scores texte, vectoriel et hybride pour `q` et `topK`. |
| Lancer l'indexation | `POST $dataset_url/graphrag/index` | Crée une tâche d'indexation à partir d'un document JSON. |
| État de la tâche | `GET $dataset_url/graphrag/status?taskId=<id>` | Lit l'état d'une tâche d'indexation. Sans `taskId`, retourne un résumé. |
| Contexte récupéré | `GET` ou `POST $dataset_url/graphrag/context` | Retourne le contexte GraphRAG pour `q`, `mode` et `topK`. |
| Réponse citée | `GET` ou `POST $dataset_url/graphrag/answer` | Retourne une réponse et ses citations ; peut appeler le fournisseur de chat. |

Les routes `search`, `context` et `answer` exigent le paramètre `q` non vide.
`topK` est optionnel et borné par la configuration du serveur. `mode` accepte
`basic`, `local`, `global` ou `drift`; il est optionnel pour `answer`, qui
utilise alors la recherche hybride établie.

La route `index` exige un objet JSON dont les trois chaînes non vides
`title`, `content` et `sourceUri` sont présentes. `sourceUri` doit être une
URI absolue. Une requête acceptée répond `202 Accepted` et inclut un `taskId` :

```bash
curl --fail --silent --show-error -H 'Content-Type: application/json' \
  -X POST "$dataset_url/graphrag/index" \
  --data '{"title":"Example","content":"Content to index","sourceUri":"urn:example:document"}'
```

La tâche crée un `Document` et un `Chunk` dans le graphe par défaut, puis
vectorise les chunks et les rapports de communautés lorsqu'un index GraphRAG
est configuré. Dans ce cas, elle peut appeler le fournisseur d'embeddings.
Les routes `index` et `answer` ont donc des effets externes et ne sont pas
appelées par `make fuseki-status`.

## Accès distant et sécurité

Vous pouvez créer un dataset sur une instance Fuseki distante avec l'API
d'administration `POST $FUSEKI_URL/$/datasets`, à condition que ce service
soit activé et que votre compte dispose du droit d'administration. Par exemple,
Fuseki accepte une requête de formulaire avec `dbName` et `dbType` (`mem`,
`tdb` ou `tdb2`) pour créer un dataset standard :

```bash
curl --fail --silent --show-error -X POST "$FUSEKI_URL/\$/datasets" \
  --data-urlencode "dbName=$GRAPHRAG_TUTORIAL_DATASET" \
  --data-urlencode 'dbType=tdb2'
```

Cette création standard ne configure pas GraphRAG. Pour exposer les routes
`/graphrag/*`, créez le service avec une configuration Fuseki qui inclut
`grag:enableGraphRAG true`, un `grag:GraphRAGIndex` et les fournisseurs requis,
comme le fait le fichier `service.ttl` généré pour le mode local. L'API
d'administration accepte aussi une configuration RDF envoyée dans le corps de
la requête, si le serveur autorise les fichiers de configuration.

Le tutoriel fournit `make fuseki-dataset-create` pour envoyer une configuration
Turtle distante, mais seulement avec `FUSEKI_MANAGED_LOCALLY=false`,
`FUSEKI_ALLOW_DATASET_CREATE=true` et
`FUSEKI_REMOTE_DATASET_CONFIG=/chemin/vers/service.ttl`. Il ne génère pas cette
configuration distante et ne crée jamais d'utilisateur. Le compte ou le proxy
utilisé doit autoriser les opérations nécessaires : administration de dataset,
lecture SPARQL, Graph Store `PUT` pour `corpus-load`, SPARQL Update pour
`rdf-lifecycle`, et les routes GraphRAG souhaitées.

Ne mettez pas un mot de passe ou un jeton dans `FUSEKI_URL` : cette valeur peut
être affichée par les cibles Make. Le tutoriel transmet toutes ses requêtes HTTP
à `curl` via la variable locale `FUSEKI_CURL_CONFIG`. Définissez-la dans
`env/.env` avec le chemin absolu d'un fichier de configuration `curl` ignoré par
Git. Ce fichier peut fournir une authentification Basic ou Digest avec un
fichier `netrc`, un certificat client TLS, ou les paramètres d'un proxy :

```dotenv
FUSEKI_CURL_CONFIG=/home/alice/.config/jena/fuseki-curl.conf
```

```text
# /home/alice/.config/jena/fuseki-curl.conf (permissions 0600)
netrc-file = "/home/alice/.config/jena/fuseki.netrc"
```

Le fichier `netrc`, également en permissions `0600`, contient les informations
de connexion pour le nom d'hôte de `FUSEKI_URL`. Il n'est ni lu ni généré par le
tutoriel. Après toute modification de `env/.env`, exécutez `make
providers-bootstrap`. La configuration est appliquée à toutes les cibles HTTP,
y compris `fuseki-ping`, les chargements Graph Store, l'indexation, les routes
GraphRAG et la vérification distante des PDF. En mode local,
`corpus-verify-pdfs` conserve le client CLI `sparql`; en mode distant, il passe
par la route SPARQL HTTP afin que cette authentification soit honorée.

La documentation générale de Fuseki est disponible dans le
[manuel Apache Jena](https://jena.apache.org/documentation/fuseki2/).