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

# Configurer et optimiser Fuseki

Ce guide prépare une instance Fuseki persistante et observable pour le
parcours GraphRAG. Il vise une instance administrée, locale ou distante ; il
ne change ni les données ni la configuration active sans action explicite.
Les mécanismes décrits proviennent de la documentation officielle Fuseki et
TDB2, liée à la fin du guide.

## 1. Définir le périmètre du service

Commencez par fixer le nom de dataset et les URL réellement consommées par le
tutoriel :

```bash
set -a
. env/generated/real-providers.env.sh
set +a

readonly dataset="$GRAPHRAG_TUTORIAL_DATASET"
readonly dataset_url="$FUSEKI_URL/$dataset"
readonly admin_url="$FUSEKI_URL/\$"
printf 'Dataset: %s\nData URL: %s\nAdmin URL: %s\n' "$dataset" "$dataset_url" "$admin_url"
```

Le Makefile du tutoriel attend les endpoints `sparql`, `update` et `data`,
puis les endpoints GraphRAG sous `/$dataset/graphrag/`. Conservez ces noms si
l'instance doit exécuter `make corpus-load`, `make rdf-lifecycle` ou les cibles
d'indexation. La liste exacte est dans la [référence API](fuseki-apis.md).

Pour une instance distante, vérifiez d'abord seulement la disponibilité :

```bash
curl --fail --silent --show-error "$admin_url/ping"
```

`/$/ping` est le point de contrôle peu coûteux défini par le protocole
d'administration Fuseki. Il ne démontre ni l'accès au dataset ni l'activation
de GraphRAG.

## 2. Déclarer un service TDB2 minimal

Fuseki décrit les services dans un graphe RDF. Un service déclare son nom, les
opérations exposées et le dataset. Cet exemple est une base TDB2 persistante :
les chemins sont ceux du **serveur**, non ceux du poste qui lance `curl`.

```turtle
@prefix :       <#> .
@prefix fuseki: <http://jena.apache.org/fuseki#> .
@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix tdb2:   <http://jena.apache.org/2016/tdb#> .

:service rdf:type fuseki:Service ;
    fuseki:name "graphrag-production" ;
    fuseki:endpoint [ fuseki:name "sparql" ; fuseki:operation fuseki:query ] ;
    fuseki:endpoint [ fuseki:name "update" ; fuseki:operation fuseki:update ] ;
    fuseki:endpoint [ fuseki:name "data" ; fuseki:operation fuseki:gsp-rw ] ;
    fuseki:dataset :dataset .

:dataset rdf:type tdb2:DatasetTDB2 ;
    tdb2:location "/srv/fuseki/databases/graphrag-production" .
```

Le service standard ci-dessus ne suffit pas à exécuter GraphRAG. Ajoutez les
classes GraphRAG au serveur, puis les propriétés `grag:enableGraphRAG true`,
`grag:graphragIndex`, les répertoires d'index et les fournisseurs. Le générateur
local [graphrag-tutorial-fuseki-config.sh](../scripts/graphrag-tutorial-fuseki-config.sh)
montre la configuration attendue par ce tutoriel. Sur un serveur distant,
adaptez les chemins des index et les noms des variables d'environnement au
serveur distant ; n'envoyez pas les secrets du poste client dans le Turtle.

Une fois déployé, contrôlez les capacités effectivement exposées :

```bash
curl --fail --silent --show-error "$dataset_url/graphrag/config"
```

## 3. Créer le dataset distant avec prudence

Fuseki peut créer un service pendant son exécution par `POST /$/datasets`. Le
tutoriel encapsule cette opération administrative dans une cible protégée :

```dotenv
FUSEKI_MANAGED_LOCALLY=false
FUSEKI_ALLOW_DATASET_CREATE=true
FUSEKI_REMOTE_DATASET_CONFIG=/chemin/serveur/service-graphrag.ttl
```

```bash
make providers-bootstrap
make fuseki-dataset-create
```

La cible envoie le contenu RDF du fichier à l'API d'administration. Elle refuse
le mode local, l'absence d'opt-in ou un fichier illisible. Une création avec le
formulaire `dbName`/`dbType=tdb2` est possible dans Fuseki, mais elle ne crée
pas les routes GraphRAG ; utilisez la description de service complète pour ce
parcours. Après la création, remettez `FUSEKI_ALLOW_DATASET_CREATE=false` et
régénérez la projection.

## 4. Réduire la surface d'exposition

Exposez seulement les opérations nécessaires. `fuseki:query`, `fuseki:update`
et `fuseki:gsp-rw` correspondent respectivement à la lecture SPARQL, aux
mises à jour et au Graph Store inscriptible. Supprimez `update` et `data` d'un
service de lecture seule plutôt que de compter sur une convention cliente.

Protégez les flux d'administration et les écritures :

- Fuseki Full utilise `shiro.ini`; sa configuration par défaut limite les
  fonctions d'administration à localhost, mais les endpoints SPARQL restent
  ouverts. Ce réglage par défaut n'est pas une politique de production.
- Fuseki Main peut activer HTTPS et l'authentification Basic ou Digest ; ces
  mécanismes peuvent aussi être déclarés dans la configuration du serveur.
- Les ACL `fuseki:allowedUsers` s'appliquent au serveur, au service ou à un
  endpoint. Donnez un compte de lecture aux clients de requêtes et un compte
  distinct aux opérations `update`, `data` et `/$/`.
- Ne placez ni mot de passe ni jeton dans `FUSEKI_URL`. Configurez
  l'authentification côté client ou proxy sans journaliser les secrets.

Les contrôles de graphe existent également dans Fuseki, mais la documentation
précise qu'ils s'appliquent actuellement aux datasets en lecture seule. Ne les
utilisez pas pour présumer une isolation d'écriture.

## 5. Limiter avant d'augmenter les ressources

L'optimisation commence par mesurer les requêtes qui consomment le plus de
temps et de mémoire. L'option de configuration `arq:queryTimeout` limite le
temps avant le premier résultat et le temps entre les résultats. `arq:updateTimeout`
suit le même format pour les mises à jour.

```turtle
@prefix fuseki: <http://jena.apache.org/fuseki#> .
@prefix ja:     <http://jena.hpl.hp.com/2005/11/Assembler#> .
@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

[] rdf:type fuseki:Server ;
   ja:context [ ja:cxtName "arq:queryTimeout" ;
                ja:cxtValue "10000,60000" ] ;
   ja:context [ ja:cxtName "arq:updateTimeout" ;
                ja:cxtValue "60000" ] .
```

Ici, la requête dispose de 10 secondes avant le premier résultat et de 60
secondes entre les résultats. Ce sont des exemples de format, pas des valeurs
universelles : fixez-les à partir de vos objectifs de service, de la taille des
résultats et des statistiques de l'instance. Vérifiez ensuite que les requêtes
légitimes passent toujours avec ces limites.

### Configurer le tas Java du serveur local

Le tutoriel démarre Fuseki avec `FUSEKI_JAVA_OPTIONS`, projetée depuis
`env/.env`. La valeur par défaut est `-Xmx4G`. Adaptez la mémoire initiale et
maximale à la mémoire disponible sur l'hôte et à un jeu de données
représentatif :

```dotenv
FUSEKI_JAVA_OPTIONS=-Xms1G -Xmx4G
```

Après modification, régénérez l'environnement puis redémarrez uniquement le
serveur local :

```bash
make providers-bootstrap
make fuseki-stop
make fuseki-start-basic
```

La JVM a besoin de mémoire pour TDB2, l'exécution des requêtes et les index
GraphRAG. Ne choisissez pas `-Xmx` à partir de la mémoire totale de la
machine : laissez de la place au système, aux autres processus et aux caches
hors tas. Mesurez les requêtes représentatives avant d'augmenter cette valeur.
`FUSEKI_JAVA_OPTIONS` n'est utilisé que si `FUSEKI_MANAGED_LOCALLY=true` ; une
instance distante doit recevoir ces options dans son propre service, conteneur
ou manifeste de déploiement.

Évitez aussi d'activer de l'inférence plus coûteuse que le besoin métier. La
documentation Jena avertit qu'une inférence étendue peut dégrader les
performances. Évaluez le raisonneur et les requêtes sur un jeu représentatif
avant de l'exposer au trafic.

## 6. Observer, sauvegarder et compacter TDB2

Les appels d'administration suivants nécessitent les droits correspondants et
une instance qui expose ces fonctions :

```bash
curl --fail --silent --show-error "$admin_url/stats/$dataset"
curl --fail --silent --show-error -X POST "$admin_url/backup/$dataset"
curl --fail --silent --show-error -X POST "$admin_url/compact/$dataset?deleteOld=true"
```

La sauvegarde et la compaction démarrent des tâches asynchrones. Conservez le
`taskId` retourné, puis suivez son état avec :

```bash
curl --fail --silent --show-error "$admin_url/tasks/<taskId>"
```

Une sauvegarde TDB2 prend une vue cohérente de la base active sans inclure les
mises à jour validées après son démarrage, tout en laissant les transactions de
lecture et d'écriture continuer. Les sauvegardes créées par l'API sont des
N-Quads compressés dans l'espace serveur : copiez-les ensuite vers un stockage
séparé et testez la restauration selon votre procédure d'exploitation.

Une compaction TDB2 peut être effectuée sur une base active : les lectures
continuent, mais les écritures attendent la fin de l'opération. Planifiez-la
hors des périodes d'écriture intensive et surveillez la tâche avant de demander
`deleteOld=true`, qui supprime l'ancienne génération après la compaction.

Le verrou `tdb.lock` empêche plusieurs JVM d'utiliser simultanément le même
répertoire TDB2. Servez un répertoire de base par l'instance Fuseki prévue et
n'ouvrez pas ce même répertoire avec un second processus JVM.

## 7. Vérification après changement

Après chaque changement de configuration, redémarrez l'instance si le mécanisme
l'exige, puis vérifiez dans cet ordre :

```bash
make fuseki-ping
make graphrag-config
make fuseki-status
```

Exécutez ensuite une requête représentative avec une limite de résultat, puis
le parcours ciblé approprié (`make corpus-verify-pdfs`, `make indexing-status`
ou `make chat-question`). Ne lancez pas une indexation ou une question de chat
pour un simple contrôle de disponibilité : ces routes peuvent appeler des
fournisseurs externes.

## Sources vérifiées

- [Configuration Fuseki](https://jena.apache.org/documentation/fuseki2/fuseki-configuration.html)
- [Protocole d'administration HTTP Fuseki](https://jena.apache.org/documentation/fuseki2/fuseki-server-protocol.html)
- [Sécurité Fuseki Full](https://jena.apache.org/documentation/fuseki2/fuseki-security.html)
- [Contrôle d'accès Fuseki Main](https://jena.apache.org/documentation/fuseki2/fuseki-data-access-control.html)
- [Administration TDB2](https://jena.apache.org/documentation/tdb2/tdb2_admin.html)
