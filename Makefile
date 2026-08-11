# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

.PHONY: graphrag-integration-ingestion graphrag-integration-api \
	graphrag-integration-chat graphrag-integration-smoke graphrag-integration

graphrag-integration-ingestion: ## Run corpus, prerequisite, ingestion, indexing, and retrieval tests
	@mvn -Pgraphrag -pl jena-graphrag-integration-tests -am \
		-Dtest=TestCorpusManifest,TestExternalProviderPrerequisites,TestGraphRAGIngestionIntegration \
		-Dsurefire.failIfNoSpecifiedTests=false test

graphrag-integration-api: ## Run GraphRAG Fuseki API contract tests
	@mvn -Pgraphrag -pl jena-graphrag-integration-tests -am \
		-Dtest=TestGraphRAGFusekiContracts,TestGraphRAGAnswerEndpoint test

graphrag-integration-chat: ## Run real-provider GraphRAG qualification
	@$(MAKE) -C jena-graphrag-integration-tests real-providers

graphrag-integration-smoke: ## Run Playwright smoke tests when the browser suite is delivered
	@printf '%s\n' 'GraphRAG Playwright smoke tests are not configured yet.' >&2
	@exit 2

graphrag-integration: graphrag-integration-ingestion graphrag-integration-api graphrag-integration-chat ## Run available GraphRAG integration suites