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

# Utiliser les CLI Apache Jena

Ce guide présente les lanceurs de la distribution binaire Apache Jena que le
tutoriel installe avec `make jena-install`. Les exemples utilisent des copies
de travail sous `target/cli`; ils n'appellent ni un fournisseur GraphRAG ni
Fuseki, sauf indication explicite. Lancez-les depuis
`jena-graphrag-tutorial/` après [l'installation Jena](getting-started.md#2-compiler-la-version-de-reference-et-linstaller).

## 1. Préparer les lanceurs

Le tutoriel installe Jena sous `JENA_INSTALL_DIR`. Chargez son profil local et
calculez le répertoire de la distribution à partir de la version du checkout :

```bash
set -a
. env/generated/real-providers.env.sh
set +a

jena_version=$(cd .. && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)
export JENA_HOME="$JENA_INSTALL_DIR/apache-jena-$jena_version"
export PATH="$JENA_HOME/bin:$PATH"

riot --version
sparql --version
```

`JENA_HOME` permet aux scripts de trouver les JARs de la distribution. Consultez
la syntaxe de la version installée avant d'utiliser une option :

```bash
riot --help
sparql --help
qparse --help
tdb2.tdbloader --help
```

## 2. Valider et convertir du RDF avec RIOT

RIOT déduit normalement la syntaxe d'une extension de fichier ; `--syntax`
permet de la déclarer lorsqu'elle est ambiguë. Pour vérifier strictement le
corpus généré par le tutoriel sans le modifier :

```bash
riot --validate data/pdf-corpus.ttl
```

Pour compter les triplets lus :

```bash
riot --count data/pdf-corpus.ttl
```

Pour produire une représentation d'échange, créez un répertoire temporaire et
convertissez le corpus en N-Quads :

```bash
mkdir -p target/cli
riot --output=NQUADS data/pdf-corpus.ttl > target/cli/pdf-corpus.nq
riot --validate target/cli/pdf-corpus.nq
```

Les formats N-Triples et N-Quads sont des sorties adaptées aux gros volumes et
aux sauvegardes ; leur écriture est en flux. Les formats Turtle et TriG
joliment formatés peuvent nécessiter une analyse globale des données et donc
davantage de mémoire.

## 3. Exécuter une requête locale avec `sparql`

`sparql` construit un dataset local en mémoire à partir de `--data`. Cette
commande ne contacte pas Fuseki et ne modifie aucun fichier RDF :

```bash
cat > target/cli/count-documents.rq <<'EOF'
PREFIX grag: <http://ormynet.com/ns/msft-graphrag#>

SELECT (COUNT(?document) AS ?documents)
WHERE {
  ?document a grag:Document .
}
EOF

sparql \
  --data=data/pdf-corpus.ttl \
  --query=target/cli/count-documents.rq \
  --results=JSON
```

La même requête peut être répétée avec `--time` pour observer sa durée :

```bash
sparql \
  --data=data/pdf-corpus.ttl \
  --query=target/cli/count-documents.rq \
  --results=JSON \
  --time
```

Ce chronométrage inclut le chargement des données en mémoire. Ne le comparez
pas directement au temps de réponse d'un endpoint Fuseki persistant. Pour une
requête HTTP sur le dataset du tutoriel, utilisez les exemples `curl` de la
[référence des API](fuseki-apis.md) ; ils respectent `FUSEKI_URL`.

## 4. Analyser une requête avec `qparse`

`qparse` analyse et réimprime une requête. L'option `--print=op` affiche son
algèbre SPARQL au format SSE, utile pour comprendre la forme évaluée par ARQ :

```bash
qparse --print=op --query=target/cli/count-documents.rq
```

Cette commande analyse la requête seulement : elle ne charge pas le corpus et
ne l'exécute pas.

## 5. Utiliser TDB2 hors ligne

Les scripts TDB2 de la distribution sont `tdb2.tdbbackup`, `tdb2.tdbdump`,
`tdb2.tdbcompact`, `tdb2.tdbloader`, `tdb2.tdbquery`, `tdb2.tdbstats` et
`tdb2.tdbupdate`. Ils sont conçus pour une base TDB2 ; ne les utilisez jamais
sur une base TDB1.

N'ouvrez pas directement avec ces outils le répertoire TDB2 actif d'un Fuseki
en cours d'exécution. TDB2 utilise `tdb.lock` pour empêcher plusieurs JVM
d'utiliser le même répertoire. Pour l'exploitation d'une base Fuseki active,
utilisez les opérations d'administration décrites dans le
[guide Fuseki](fuseki-configuration-optimization.md#6-observer-sauvegarder-et-compacter-tdb2).

Pour créer une base TDB2 **distincte** à partir du corpus, validez d'abord le
RDF puis chargez-la :

```bash
riot --validate data/pdf-corpus.ttl
rm -rf target/cli/tdb2
mkdir -p target/cli/tdb2
tdb2.tdbloader --loc target/cli/tdb2 data/pdf-corpus.ttl
```

`tdb2.tdbloader` propose plusieurs chargeurs. Le choix par défaut `phased`
cherche un compromis entre performance et ressources ; les chargeurs les plus
rapides peuvent consommer fortement CPU, E/S et mémoire, et ne fournissent pas
tous la même isolation si le chargement échoue. Pour une première exécution,
conservez donc le défaut et testez avec vos données et votre matériel.

Interrogez ensuite cette base hors ligne et exportez-la :

```bash
tdb2.tdbquery \
  --loc target/cli/tdb2 \
  --query=target/cli/count-documents.rq \
  --results=JSON

tdb2.tdbdump --loc target/cli/tdb2 --output=NQUADS > target/cli/tdb2-dump.nq
riot --validate target/cli/tdb2-dump.nq
```

Supprimez cette base d'essai lorsque vous n'en avez plus besoin :

```bash
rm -rf target/cli
```

## Sources vérifiées

- [Outils en ligne de commande ARQ](https://jena.apache.org/documentation/query/cmds.html)
- [Lecture RDF avec RIOT](https://jena.apache.org/documentation/io/rdf-input.html)
- [Écriture RDF avec RIOT](https://jena.apache.org/documentation/io/rdf-output.html)
- [Outils en ligne de commande TDB2](https://jena.apache.org/documentation/tdb2/tdb2_cmds.html)
- [Administration TDB2](https://jena.apache.org/documentation/tdb2/tdb2_admin.html)
