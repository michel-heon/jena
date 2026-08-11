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
	graphrag-integration-chat graphrag-integration-smoke \
	graphrag-integration-disabled-graphrag-smoke \
	graphrag-integration-real-providers-smoke graphrag-integration-ultimate-pdf-corpus-smoke \
	graphrag-integration-exhaustive-smoke graphrag-integration-report \
	graphrag-integration

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
	@npm --prefix jena-graphrag-integration-tests ci
	@npm --prefix jena-graphrag-integration-tests exec -- playwright install chromium
	@$(MAKE) -C jena-graphrag-integration-tests smoke

graphrag-integration-disabled-graphrag-smoke: ## Run Fuseki UI Playwright smoke tests with GraphRAG disabled
	@npm --prefix jena-graphrag-integration-tests ci
	@npm --prefix jena-graphrag-integration-tests exec -- playwright install chromium
	@$(MAKE) -C jena-graphrag-integration-tests disabled-graphrag-smoke

graphrag-integration-real-providers-smoke: ## Run the real-provider Fuseki UI Playwright smoke suite
	@npm --prefix jena-graphrag-integration-tests ci
	@npm --prefix jena-graphrag-integration-tests exec -- playwright install chromium
	@$(MAKE) -C jena-graphrag-integration-tests real-providers-smoke

graphrag-integration-ultimate-pdf-corpus-smoke: ## Ingest all GraphRAG corpus PDFs and run five cited provider chats
	@npm --prefix jena-graphrag-integration-tests ci
	@npm --prefix jena-graphrag-integration-tests exec -- playwright install chromium
	@$(MAKE) -C jena-graphrag-integration-tests ultimate-pdf-corpus-smoke

graphrag-integration-exhaustive-smoke: ## Run all Tranche 6 and 7 Fuseki UI Playwright smoke suites
	@npm --prefix jena-graphrag-integration-tests ci
	@npm --prefix jena-graphrag-integration-tests exec -- playwright install chromium
	@$(MAKE) -C jena-graphrag-integration-tests exhaustive-smoke

graphrag-integration-report: ## Serve the Playwright report and open it in the Windows browser from WSL
	@$(MAKE) -C jena-graphrag-integration-tests report

graphrag-integration: graphrag-integration-ingestion graphrag-integration-api graphrag-integration-chat graphrag-integration-smoke ## Run all GraphRAG integration suites