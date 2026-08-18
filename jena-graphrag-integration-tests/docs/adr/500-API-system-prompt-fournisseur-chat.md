<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements. See the NOTICE file
   distributed with this work for additional information regarding
   copyright ownership. The ASF licenses this file to You under the
   Apache License, Version 2.0 (the "License"); you may not use this
   file except in compliance with the License. You may obtain a copy of
   the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   SPDX-License-Identifier: Apache-2.0
-->

---
adr: 500
title: "System prompt optionnel des fournisseurs chat GraphRAG"
status: "accepted"
date: 2026-08-11
superseded_by: null
replaces: []
related_adrs: [101, 602]
related_issues: [2]
classification:
  lifecycle: "accepted"
  domain: "api"
  impact: "medium"
  quality: ["security", "maintainability", "reliability"]
  reversibility: "moderate"
  scope: "tactical"
  tech_areas: ["jena-graphrag", "fuseki", "http", "playwright", "integration-testing"]
tags: ["graphrag", "chat", "system-prompt", "security"]
stakeholders: ["jena-graphrag maintainers", "integration-test contributors"]
effort: "low"
---

# ADR 500 : System prompt optionnel des fournisseurs chat GraphRAG

## Vue d'ensemble

Un service Fuseki GraphRAG peut transmettre une instruction optionnelle au fournisseur HTTP de chat. La configuration contient uniquement le nom d'une variable d'environnement; la valeur est résolue au démarrage, utilisée pour la requête sortante et n'est pas exposée par les routes GraphRAG, les erreurs ni les journaux du fournisseur.

## Contexte et problème

Les réponses réelles de GraphRAG doivent pouvoir recevoir une instruction opérationnelle, sans ajouter un secret ou une règle interne dans le modèle RDF Fuseki, l'API `/{dataset}/graphrag/config` ou les artefacts Playwright. Le contrat antérieur ne transmettait que la question et le contexte.

## Décision

La ressource de service Fuseki accepte `grag:systemPromptEnv`, dont la valeur est le nom d'une variable d'environnement en majuscules, chiffres et soulignements. Une variable absente ou vide empêche le démarrage avec une erreur de configuration. `GraphRAGConfiguration` conserve seulement la valeur résolue en mémoire et `GraphRAGAnswerAction` la passe au `ChatCompletionProvider`.

`HttpChatCompletionProvider` ajoute l'instruction non vide avant la question et le contexte dans le contenu de la requête envoyée au fournisseur compatible OpenAI. Il conserve la désactivation des journaux de requêtes et réponses. Les fournisseurs qui n'implémentent pas ce mécanisme restent compatibles grâce à la méthode par défaut de l'interface.

Le serveur UI de qualification associe `GRAPHRAG_SYSTEM_PROMPT` à `grag:systemPromptEnv` seulement pour le parcours fournisseurs réels. Le lanceur donne une instruction non sensible par défaut si cette variable n'est pas fournie; il ne l'affiche jamais.

## Alternatives considérées

- Ajouter le texte dans le fichier RDF Fuseki : rejeté, car il serait versionné ou visible dans la configuration.
- Exposer l'instruction dans `/graphrag/config` : rejeté, car cette route est publique pour les opérateurs et les parcours navigateur.
- Modifier la signature fonctionnelle existante du provider : rejeté, car les implémentations déterministes et lambdas existantes perdraient leur compatibilité.

## Conséquences

Les opérateurs peuvent adapter le comportement du fournisseur sans changer le corpus ni les paramètres publics de GraphRAG. La valeur reste cependant une donnée sensible potentielle: elle ne doit pas être affichée dans les commandes, rapports ou assertions. Le budget de tokens inclut l'instruction, ce qui peut faire échouer une requête qui dépasse la limite configurée.

## Plan d'implémentation

1. Ajouter le terme RDF `grag:systemPromptEnv` et sa résolution validée.
2. Transmettre l'instruction au provider HTTP sans journaliser la requête.
3. Couvrir la résolution, l'absence de variable et la requête HTTP avec des sentinelles de test.
4. Exécuter le smoke navigateur fournisseurs réels avec la variable configurée et vérifier une réponse citée sans inspecter son texte.

## Critères de succès et validation

- Les tests unitaires prouvent la résolution de la variable et l'inclusion de l'instruction dans la requête HTTP.
- Le smoke fournisseurs réels obtient une réponse non vide avec une citation du document indexé.
- La réponse de `/graphrag/config` ne contient aucun champ `systemPrompt`.
- Les tests d'erreur publique et les logs du provider ne divulguent aucune valeur de configuration.

## Traçabilité et liens

- Issue : [#2](https://github.com/michel-heon/jena/issues/2)
- [ADR-101](./101-ARCH-adoption-langchain4j-couche-provider.md)
- [ADR-602](./602-DEVOPS-makefile-orchestrateur.md)
- [Contrat `GraphRAGConfiguration`](../../../jena-graphrag/src/main/java/org/apache/jena/graphrag/fuseki/GraphRAGConfiguration.java)
