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

# AI contributors: update README.md when adding, removing, or changing a public
# Makefile rule or its behavior, whenever the tutorial documentation is affected.

SHELL := /bin/bash
.DEFAULT_GOAL := help

TUTORIAL_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
MODULE_DIR := $(abspath $(TUTORIAL_DIR)/../../..)
ROOT_DIR := $(abspath $(MODULE_DIR)/..)
-include $(MODULE_DIR)/env/generated/real-providers.mk
WORK_DIR := $(MODULE_DIR)/target/tranche-7-tutoriel
GRAPHRAG_TUTORIAL_PDF_PATH ?= src/test/resources/corpus/ingestion/pdf
GRAPHRAG_TUTORIAL_CORPUS_PATH ?= target/tranche-7-tutoriel/pdf-corpus.ttl
GRAPHRAG_TUTORIAL_DATASET ?= graphrag-smoke
GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT ?= 12
PDF_DIR := $(abspath $(MODULE_DIR)/$(GRAPHRAG_TUTORIAL_PDF_PATH))
CLASSPATH_FILE := $(WORK_DIR)/classpath
CORPUS_FILE := $(abspath $(MODULE_DIR)/$(GRAPHRAG_TUTORIAL_CORPUS_PATH))
SEMANTIC_MARKER := $(WORK_DIR)/semantic-extraction.done
SEMANTIC_CHECKPOINT := $(WORK_DIR)/semantic-extraction.properties
PORT_FILE := $(WORK_DIR)/port
PID_FILE := $(WORK_DIR)/fuseki.pid
TASK_FILE := $(WORK_DIR)/task-id
LOG_FILE := $(WORK_DIR)/fuseki.log
MATERIALIZER_SOURCE := $(MODULE_DIR)/src/test/java/org/apache/jena/graphrag/integration/PdfCorpusMaterializer.java
ARQ_CLASS := $(ROOT_DIR)/jena-cmds/target/classes/arq/arq.class
DATASET := $(GRAPHRAG_TUTORIAL_DATASET)
CHAT_MODE_QUESTION ?= What is GraphRAG?
SYSTEM_PROMPT := Answer only from the ingested GraphRAG research PDF corpus. Cite retrieved sources and say when the corpus lacks the answer.
FUSEKI_URL = http://127.0.0.1:$$(cat "$(PORT_FILE)")
WINDOWS_FUSEKI_URL = http://$$(hostname -I | awk '{print $$1}'):$$(cat "$(PORT_FILE)")

.PHONY: help providers-bootstrap pdfs-check project-prepare corpus-materialize \
	corpus-semantic-extract fuseki-start fuseki-ping fuseki-status fuseki-open graphrag-config indexing-start indexing-status \
	indexing-wait corpus-verify-pdfs corpus-statistic graph-cat ttt-cat entities-list relationships-list communities-list findings-list chat-ask-questions chat-ask-modes chat-question context-question fuseki-stop tutorial-clean tutorial-run

help: ## Display tutorial steps
	@printf '%s\n' \
		'GraphRAG PDF tutorial' \
		'' \
		'Recommended sequence:' \
		'  make providers-bootstrap corpus-materialize corpus-semantic-extract' \
		'  make corpus-statistic fuseki-start fuseki-ping indexing-start indexing-wait' \
		'' \
		'Preparation' \
		'  providers-bootstrap       Generate the local provider environment.' \
		'  pdfs-check                Verify the configured PDF corpus.' \
		'  project-prepare           Compile the tutorial dependencies.' \
		'  tutorial-clean            Remove the temporary tutorial state.' \
		'' \
		'Corpus' \
		'  corpus-materialize        Ingest PDFs into the local Turtle corpus.' \
		'  corpus-semantic-extract   Extract entities, relationships, and communities.' \
		'  corpus-statistic          Show local corpus and semantic graph counts.' \
		'  graph-cat                 Dump the local RDF graph page by page with Jena.' \
		'' \
		'Fuseki and indexing' \
		'  fuseki-start              Start Fuseki with the enriched corpus.' \
		'  fuseki-ping               Confirm that Fuseki is reachable.' \
		'  fuseki-status             Report Fuseki process and HTTP status.' \
		'  fuseki-open               Open the Fuseki UI.' \
		'  graphrag-config           Show the public GraphRAG configuration.' \
		'  indexing-start            Start vector indexing.' \
		'  indexing-status           Show vector indexing status.' \
		'  indexing-wait             Wait for vector indexing to finish.' \
		'' \
		'Validation and questions' \
		'  corpus-verify-pdfs        Verify the PDFs loaded in Fuseki.' \
		'  entities-list             List extracted entities from the local Turtle corpus.' \
		'  relationships-list        List extracted relationships from the local Turtle corpus.' \
		'  communities-list          List extracted communities.' \
		'  findings-list             List extracted findings from the local Turtle corpus.' \
		'  chat-ask-questions        Run the five tutorial questions.' \
		'  chat-ask-modes            Qualify basic, local, and global with real providers.' \
		'  chat-question             Ask one question: make chat-question QUESTION="..." MODE=local' \
		'  context-question          Inspect context: make context-question QUESTION="..." MODE=local' \
		'' \
		'All steps' \
		'  tutorial-run              Run the complete tutorial workflow.' \
		'  fuseki-stop               Stop the Fuseki server.'

providers-bootstrap: ## Generate the ignored real-provider environment
	@$(MAKE) -C $(MODULE_DIR) bootstrap-real-providers

pdfs-check: ## Verify the configured PDF fixture count
	@count=$$(find "$(PDF_DIR)" -maxdepth 1 -type f -iname '*.pdf' | wc -l); \
	[[ "$$count" == "$(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT)" ]] || { echo "Expected $(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT) PDF fixtures, found $$count." >&2; exit 1; }; \
	echo "$$count PDF fixture(s) found."

$(CLASSPATH_FILE): $(MATERIALIZER_SOURCE)
	@mkdir -p "$(WORK_DIR)"
	@cd "$(ROOT_DIR)" && mvn -q -Drat.skip=true -Pgraphrag -pl jena-graphrag-integration-tests -am package -DskipTests
	@cd "$(ROOT_DIR)" && mvn -q -Pgraphrag -pl jena-graphrag-integration-tests dependency:build-classpath \
		-DincludeScope=test -Dmdep.outputFile="$(CLASSPATH_FILE)"

project-prepare: $(CLASSPATH_FILE) ## Compile the module and create its test classpath
	@echo "Classpath prepared in $(CLASSPATH_FILE)."

$(ARQ_CLASS):
	@cd "$(ROOT_DIR)" && mvn -q -Drat.skip=true -pl jena-cmds -am package -DskipTests

$(CORPUS_FILE): $(MODULE_DIR)/env/generated/real-providers.mk $(CLASSPATH_FILE) $(MATERIALIZER_SOURCE)
	@rm -f "$(SEMANTIC_MARKER)"
	@java -cp "$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
		org.apache.jena.graphrag.integration.PdfCorpusMaterializer "$(PDF_DIR)" "$@"
	@test -s "$@"

corpus-materialize: pdfs-check $(CORPUS_FILE) ## Materialize the PDFs through DocumentIngestionService
	@echo "PDF corpus materialized at $(CORPUS_FILE)."

$(SEMANTIC_MARKER): $(CORPUS_FILE) $(CLASSPATH_FILE) $(MATERIALIZER_SOURCE)
	@env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	[[ -r "$$env_file" ]] || { echo "Run make providers-bootstrap first." >&2; exit 1; }; \
	set -a; source "$$env_file"; set +a; \
	java -cp "$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
		org.apache.jena.graphrag.integration.PdfCorpusMaterializer "$(PDF_DIR)" "$(CORPUS_FILE)" --semantic "$(SEMANTIC_CHECKPOINT)" && \
		touch "$@"

corpus-semantic-extract: pdfs-check $(SEMANTIC_MARKER) ## Extract entities, relationships, and communities from the PDF chunks
	@echo "Semantic graph materialized in $(CORPUS_FILE)."

$(PORT_FILE):
	@mkdir -p "$(WORK_DIR)"
	@node -e 'const server = require("net").createServer(); server.listen(0, "127.0.0.1", () => { console.log(server.address().port); server.close(); });' > "$@"

fuseki-start: $(SEMANTIC_MARKER) $(PORT_FILE) ## Start production Fuseki with the enriched corpus
	@if [[ -f "$(PID_FILE)" ]] && kill -0 "$$(cat "$(PID_FILE)")" 2>/dev/null; then \
		echo "Fuseki is already running at $(FUSEKI_URL)."; \
	else \
		env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
		[[ -r "$$env_file" ]] || { echo "Run make bootstrap first." >&2; exit 1; }; \
		set -a; source "$$env_file"; set +a; \
		GRAPHRAG_SYSTEM_PROMPT='$(SYSTEM_PROMPT)' \
		java -cp "$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			org.apache.jena.graphrag.fuseki.GraphRAGFusekiUIServer "$(CORPUS_FILE)" "$$(cat "$(PORT_FILE)")" "$(DATASET)" true "$(WORK_DIR)/real-provider-index" \
			> "$(LOG_FILE)" 2>&1 & echo $$! > "$(PID_FILE)"; \
		echo "Fuseki started at $(FUSEKI_URL)."; \
		echo "Log: $(LOG_FILE)"; \
	fi

fuseki-ping: ## Confirm Fuseki is reachable
	@curl --fail --silent --show-error "$(FUSEKI_URL)/\$$/ping" >/dev/null
	@echo "Fuseki responds to ping."

fuseki-status: ## Report Fuseki process, ping, and dataset status
	@port=unknown; [[ -s "$(PORT_FILE)" ]] && port=$$(cat "$(PORT_FILE)"); \
	pid=unknown; [[ -s "$(PID_FILE)" ]] && pid=$$(cat "$(PID_FILE)"); \
	echo "Fuseki status"; \
	echo "PID: $$pid"; \
	echo "URL: http://127.0.0.1:$$port"; \
	echo "Dataset: $(DATASET)"; \
	if [[ "$$pid" != unknown ]] && kill -0 "$$pid" 2>/dev/null; then echo "Process: running"; else echo "Process: not running"; fi; \
	if [[ "$$port" != unknown ]] && curl --fail --silent --show-error "http://127.0.0.1:$$port/\$$/ping" >/dev/null 2>&1; then \
		echo "Ping: reachable"; \
		base="http://127.0.0.1:$$port/$(DATASET)/graphrag"; \
		if curl --fail --silent --show-error "$$base/config" >/dev/null 2>&1; then echo "API config: available"; else echo "API config: unavailable"; fi; \
		if curl --fail --silent --show-error -G "$$base/context" --data-urlencode 'q=fuseki-status' --data-urlencode 'mode=local' --data-urlencode 'topK=1' >/dev/null 2>&1; then echo "API context: available"; else echo "API context: unavailable"; fi; \
		if [[ -s "$(TASK_FILE)" ]]; then \
			task_id=$$(cat "$(TASK_FILE)"); \
			if curl --fail --silent --show-error "$$base/status?taskId=$$task_id" >/dev/null 2>&1; then echo "API status: available (task $$task_id)"; else echo "API status: unavailable (task $$task_id)"; fi; \
		else \
			echo "API status: not checked (no indexing task)"; \
		fi; \
		echo "API answer: not probed (would call the chat provider)"; \
		echo "API index: not probed (would start indexing)"; \
	else \
		echo "Ping: unreachable"; \
	fi

fuseki-open: fuseki-ping ## Open the Fuseki UI in the Windows default browser from WSL
	@url="$(WINDOWS_FUSEKI_URL)"; \
	powershell.exe -NoProfile -Command "Start-Process '$$url'"; \
	echo "Opened $$url in the Windows default browser."

graphrag-config: ## Show the public GraphRAG configuration
	@curl --fail --silent --show-error "$(FUSEKI_URL)/$(DATASET)/graphrag/config"
	@echo

indexing-start: ## Trigger public indexing and save the returned task identifier
	@response=$$(curl --fail --silent --show-error -H 'Content-Type: application/json' -X POST \
		"$(FUSEKI_URL)/$(DATASET)/graphrag/index" \
		--data '{"title":"PDF corpus vectorization trigger","content":"Index the GraphRAG PDF corpus prepared by the production ingestion service.","sourceUri":"urn:graphrag:tutorial-pdf-corpus-trigger"}'); \
	printf '%s\n' "$$response" > "$(WORK_DIR)/index-response.json"; \
	printf '%s' "$$response" | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const taskId = JSON.parse(input).taskId; if (!taskId) process.exit(1); console.log(taskId); });' > "$(TASK_FILE)"; \
	echo "Index task: $$(cat "$(TASK_FILE)")."

indexing-status: ## Display the current indexing task state
	@[[ -s "$(TASK_FILE)" ]] || { echo "Run make indexing-start first." >&2; exit 1; }
	@task=$$(curl --fail --silent --show-error "$(FUSEKI_URL)/$(DATASET)/graphrag/status?taskId=$$(cat "$(TASK_FILE)")"); \
	metrics=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/sparql" \
		--data-urlencode 'query=PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(DISTINCT ?document) AS ?documents) (COUNT(DISTINCT ?pdf) AS ?pdfDocuments) (COUNT(DISTINCT ?chunk) AS ?chunks) WHERE { { ?document a grag:Document . } UNION { ?chunk a grag:Chunk . } OPTIONAL { ?pdf a grag:Document ; grag:sourceFile ?sourceFile . FILTER(STRENDS(LCASE(STR(?sourceFile)), ".pdf")) } }' \
		--data-urlencode 'format=application/sparql-results+json'); \
	TASK="$$task" METRICS="$$metrics" node -e 'const task = JSON.parse(process.env.TASK); const bindings = JSON.parse(process.env.METRICS).results.bindings[0]; const count = name => bindings[name]?.value ?? "0"; const started = task.startedAt ? new Date(task.startedAt) : null; const ended = task.completedAt ? new Date(task.completedAt) : null; const elapsed = started ? Math.floor(((ended ?? new Date()) - started) / 1000) : null; console.log("Task: " + task.taskId); console.log("State: " + task.status); console.log("Documents loaded: " + count("documents")); console.log("PDF documents: " + count("pdfDocuments")); console.log("Chunks to vectorize: " + count("chunks")); if (task.status === "done") { console.log("Indexed chunks: " + count("chunks") + " / " + count("chunks")); console.log("Indexing progress: 100%"); } else { console.log("Indexed chunks: not exposed while the task is running"); console.log("Indexing progress: not exposed by the GraphRAG task API"); } if (elapsed !== null) console.log("Elapsed: " + elapsed + "s"); if (task.error) console.log("Error: " + task.error);'

indexing-wait: ## Wait until the indexing task completes
	@[[ -s "$(TASK_FILE)" ]] || { echo "Run make indexing-start first." >&2; exit 1; }; \
	while true; do \
		response=$$(curl --fail --silent --show-error "$(FUSEKI_URL)/$(DATASET)/graphrag/status?taskId=$$(cat "$(TASK_FILE)")"); \
		status=$$(printf '%s' "$$response" | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).status));'); \
		echo "Index status: $$status"; \
		[[ "$$status" == done ]] && break; \
		[[ "$$status" == failed ]] && { printf '%s\n' "$$response" >&2; exit 1; }; \
		sleep 5; \
	done

corpus-verify-pdfs: ## Assert the configured number of PDF source files are stored
	@response=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/sparql" \
		--data-urlencode 'query=PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(DISTINCT ?sourceFile) AS ?count) WHERE { ?document a grag:Document ; grag:sourceFile ?sourceFile . FILTER(STRENDS(LCASE(STR(?sourceFile)), ".pdf")) }' \
		--data-urlencode 'format=application/sparql-results+json'); \
	count=$$(printf '%s' "$$response" | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).results.bindings[0].count.value));'); \
	[[ "$$count" == "$(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT)" ]] || { echo "Expected $(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT) PDF source files, found $$count." >&2; exit 1; }; \
	echo "$$count PDF source file(s) found."

corpus-statistic: pdfs-check $(CORPUS_FILE) $(ARQ_CLASS) ## Display GraphRAG resource and structural-link statistics
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?triples ?documents ?pdfDocuments ?chunks ?entities ?relationships ?communities ?findings ?partOfLinks ?entityLinks ?relatedToLinks ?communityLinks WHERE { { SELECT (COUNT(*) AS ?triples) WHERE { ?subject ?predicate ?object } } { SELECT (COUNT(DISTINCT ?document) AS ?documents) WHERE { ?document a grag:Document } } { SELECT (COUNT(DISTINCT ?pdfDocument) AS ?pdfDocuments) WHERE { ?pdfDocument a grag:Document ; grag:sourceFile ?sourceFile . FILTER(STRENDS(LCASE(STR(?sourceFile)), ".pdf")) } } { SELECT (COUNT(DISTINCT ?chunk) AS ?chunks) WHERE { ?chunk a grag:Chunk } } { SELECT (COUNT(DISTINCT ?entity) AS ?entities) WHERE { ?entity a grag:Entity } } { SELECT (COUNT(DISTINCT ?relationship) AS ?relationships) WHERE { ?relationship a grag:Relationship } } { SELECT (COUNT(DISTINCT ?community) AS ?communities) WHERE { ?community a grag:Community } } { SELECT (COUNT(DISTINCT ?finding) AS ?findings) WHERE { ?finding a grag:Finding } } { SELECT (COUNT(*) AS ?partOfLinks) WHERE { ?chunk grag:partOf ?document } } { SELECT (COUNT(*) AS ?entityLinks) WHERE { ?chunk grag:hasEntity ?entity } } { SELECT (COUNT(*) AS ?relatedToLinks) WHERE { ?source grag:relatedTo ?target } } { SELECT (COUNT(*) AS ?communityLinks) WHERE { ?entity grag:inCommunity ?community } } }' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const row = JSON.parse(input).results.bindings[0]; const count = name => Number(row[name]?.value ?? 0).toLocaleString("en-US"); const line = (label, name) => console.log("  " + label.padEnd(36) + count(name)); console.log("GraphRAG corpus statistics"); console.log(""); console.log("Resources"); line("Triples", "triples"); line("Documents", "documents"); line("PDF documents", "pdfDocuments"); line("Chunks", "chunks"); console.log(""); console.log("Semantic enrichment"); line("Entities", "entities"); line("Relationships", "relationships"); line("Communities", "communities"); line("Findings", "findings"); console.log(""); console.log("Structural links"); line("Chunk -> document (partOf)", "partOfLinks"); line("Chunk -> entity (hasEntity)", "entityLinks"); line("Entity -> entity (relatedTo)", "relatedToLinks"); line("Entity -> community (inCommunity)", "communityLinks"); });'

graph-cat: $(CORPUS_FILE) $(CLASSPATH_FILE) $(ARQ_CLASS) ## Display the RDF graph content page by page with the Jena command
	@echo "Graph content for $(CORPUS_FILE)"
	@java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
		jena.riot --output=TURTLE "$(CORPUS_FILE)" | more

entities-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG entities from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?entity ?name WHERE { ?entity a grag:Entity ; grag:name ?name } ORDER BY LCASE(STR(?name))' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG entities found."); process.exit(0); } for (const entity of bindings) console.log(entity.name.value + "\n  " + entity.entity.value); });'

relationships-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG relationships from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?sourceName ?description ?targetName WHERE { ?relationship a grag:Relationship ; grag:source ?source ; grag:target ?target . ?source grag:name ?sourceName . ?target grag:name ?targetName . OPTIONAL { ?relationship grag:description ?description } } ORDER BY LCASE(STR(?sourceName)) LCASE(STR(?targetName))' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG relationships found."); process.exit(0); } for (const relationship of bindings) console.log(relationship.sourceName.value + " --" + (relationship.description?.value ?? "related to") + "--> " + relationship.targetName.value); });'

communities-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG communities and their available summaries locally
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?community ?title ?summary ?fullContent WHERE { ?community a grag:Community . OPTIONAL { ?community grag:title ?title } OPTIONAL { ?community grag:summary ?summary } OPTIONAL { ?community grag:fullContent ?fullContent } } ORDER BY STR(?community)' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG communities found."); process.exit(0); } for (const community of bindings) { console.log("Community: " + community.community.value); if (community.title) console.log("Title: " + community.title.value); if (community.summary) console.log("Summary: " + community.summary.value); if (community.fullContent) console.log("Full content: " + community.fullContent.value); console.log(""); } });'

findings-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG findings from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?finding ?summary ?fullContent WHERE { ?finding a grag:Finding . OPTIONAL { ?finding grag:summary ?summary } OPTIONAL { ?finding grag:fullContent ?fullContent } } ORDER BY STR(?finding)' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/test-classes:$(MODULE_DIR)/target/classes:$(ROOT_DIR)/jena-graphrag/target/classes:$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG findings found."); process.exit(0); } for (const finding of bindings) { console.log("Finding: " + finding.finding.value); if (finding.summary) console.log("Summary: " + finding.summary.value); if (finding.fullContent) console.log("Full content: " + finding.fullContent.value); console.log(""); } });'

chat-ask-questions: ## Ask five questions and verify cited PDF answers
	@for question in \
		'What is GraphRAG?' \
		'What is the difference between local and global GraphRAG?' \
		'How can an RDF knowledge graph support GraphRAG?' \
		'What is KG2RAG?' \
		'How is SPARQL generated from natural language over federated knowledge graphs?'; do \
		echo "Question: $$question"; \
		curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$$question" --data-urlencode 'topK=5' | \
			node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const response = JSON.parse(input); const citations = response.citations ?? []; const pdfCitations = citations.filter(citation => /^http:\/\/ormynet\.com\/ns\/data#chunk-/.test(citation.uri)); if (!response.answer || !response.answer.trim() || pdfCitations.length === 0) process.exit(1); console.log("Answer:\n" + response.answer.trim()); console.log("PDF citations:"); for (const citation of pdfCitations) console.log("  " + citation.uri); });'; \
		echo; \
	done

chat-ask-modes: ## Qualify basic, local, and global answers against their retrieved citations
	@set -e; for mode in basic local global; do \
		question='$(CHAT_MODE_QUESTION)'; \
		echo "Mode: $$mode"; \
		echo "Question: $$question"; \
		context=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/context" --data-urlencode "q=$$question" --data-urlencode "mode=$$mode" --data-urlencode 'topK=5'); \
		response=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$$question" --data-urlencode "mode=$$mode" --data-urlencode 'topK=5'); \
		CONTEXT="$$context" RESPONSE="$$response" MODE="$$mode" node -e 'const context = JSON.parse(process.env.CONTEXT); const response = JSON.parse(process.env.RESPONSE); const expected = context.results.map(result => [result.uri, result.sourceText]); const citations = (response.citations ?? []).map(citation => [citation.uri, citation.text]); const abstention = "Aucun contexte GraphRAG correspondant a la question."; const allowedTypes = {basic: ["chunk"], local: ["relationship", "entity", "chunk", "community"], global: ["community"]}; if (!response.answer || !response.answer.trim()) process.exit(1); if (response.answer === abstention) { if (expected.length !== 0 || citations.length !== 0) process.exit(1); console.log("Status: abstention (no matching mode context)."); } else { const types = [...new Set(context.results.map(result => result.type))]; if (expected.length === 0 || JSON.stringify(citations) !== JSON.stringify(expected) || types.some(type => !allowedTypes[process.env.MODE].includes(type))) process.exit(1); console.log("Status: cited answer."); console.log("Context types: " + types.join(", ")); if (process.env.MODE === "local" && types.length < 2) console.log("Qualification limit: local context is not mixed for this corpus and question."); if (process.env.MODE === "global" && context.results.length < 2) console.log("Qualification limit: global context has fewer than two community reports."); console.log("Answer:\n" + response.answer.trim()); console.log("Mode citations:"); for (const citation of citations) console.log("  " + citation[0]); }'; \
		echo; \
	done

chat-question: ## Ask QUESTION and display citations, optionally using MODE=basic, local, or global
	@[[ -n "$(QUESTION)" ]] || { echo 'Usage: make chat-question QUESTION="Your question" [MODE=local]' >&2; exit 1; }
	@[[ -z "$(MODE)" || "$(MODE)" == basic || "$(MODE)" == local || "$(MODE)" == global ]] || { echo 'MODE must be basic, local, or global.' >&2; exit 1; }
	@echo "Question: $(QUESTION)"
	@curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$(QUESTION)" --data-urlencode 'topK=5' $(if $(MODE),--data-urlencode "mode=$(MODE)") | \
		MODE="$(MODE)" node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const response = JSON.parse(input); const citations = response.citations ?? []; const displayedCitations = process.env.MODE ? citations : citations.filter(citation => /^http:\/\/ormynet\.com\/ns\/data#chunk-/.test(citation.uri)); if (!response.answer || !response.answer.trim() || (!process.env.MODE && displayedCitations.length === 0)) process.exit(1); console.log("Answer:\n" + response.answer.trim()); if (displayedCitations.length === 0) { console.log("No matching mode context."); return; } console.log(process.env.MODE ? "Mode citations:" : "PDF citations:"); for (const citation of displayedCitations) console.log("  " + citation.uri); });'

context-question: ## Retrieve context for QUESTION with MODE=basic, local, or global
	@[[ -n "$(QUESTION)" ]] || { echo 'Usage: make context-question QUESTION="Your question" MODE=local' >&2; exit 1; }
	@[[ "$(MODE)" == basic || "$(MODE)" == local || "$(MODE)" == global ]] || { echo 'MODE must be basic, local, or global.' >&2; exit 1; }
	@echo "Question: $(QUESTION)"
	@echo "Mode: $(MODE)"
	@context=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/context" --data-urlencode "q=$(QUESTION)" --data-urlencode "mode=$(MODE)" --data-urlencode 'topK=5'); \
	metrics=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/sparql" \
		--data-urlencode 'query=PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(DISTINCT ?entity) AS ?entities) (COUNT(DISTINCT ?relationship) AS ?relationships) (COUNT(DISTINCT ?community) AS ?communities) WHERE { OPTIONAL { ?entity a grag:Entity . } OPTIONAL { ?relationship a grag:Relationship . } OPTIONAL { ?community a grag:Community . } }' \
		--data-urlencode 'format=application/sparql-results+json'); \
	CONTEXT="$$context" METRICS="$$metrics" MODE="$(MODE)" node -e 'const context = JSON.parse(process.env.CONTEXT); const metrics = JSON.parse(process.env.METRICS).results.bindings[0]; const count = name => metrics[name]?.value ?? "0"; console.log(JSON.stringify(context, null, 2)); if (context.results.length !== 0) process.exit(0); console.log("No context result."); console.log("Graph entities: " + count("entities") + ", relationships: " + count("relationships") + ", communities: " + count("communities") + "."); if (process.env.MODE === "basic") console.log("basic uses the configured text index, or a literal fallback match when no text index is available."); if (process.env.MODE === "local") console.log("local requires GraphRAG entities and relationships; PDF ingestion alone creates documents and chunks only."); if (process.env.MODE === "global") console.log("global requires indexed GraphRAG communities with summary or fullContent values.");'

fuseki-stop: ## Stop the tutorial Fuseki process
	@if [[ -f "$(PID_FILE)" ]]; then \
		pid=$$(cat "$(PID_FILE)"); \
		if kill -0 "$$pid" 2>/dev/null; then kill "$$pid"; fi; \
		rm -f "$(PID_FILE)"; \
		echo "Fuseki stopped."; \
	else \
		echo "Fuseki is not running."; \
	fi

tutorial-clean: fuseki-stop ## Remove all temporary tutorial state
	@rm -rf "$(WORK_DIR)"
	@echo "Tutorial state removed."

tutorial-run: providers-bootstrap pdfs-check project-prepare corpus-materialize fuseki-start fuseki-ping graphrag-config indexing-start indexing-wait corpus-verify-pdfs chat-ask-questions ## Run every tutorial step in sequence
	@echo "Tranche 7 tutorial completed successfully. Run make fuseki-stop when you are finished with Fuseki."