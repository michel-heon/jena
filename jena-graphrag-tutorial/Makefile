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
MODULE_DIR := $(TUTORIAL_DIR)
ROOT_DIR := $(abspath $(MODULE_DIR)/..)
-include $(MODULE_DIR)/env/generated/real-providers.mk
WORK_DIR := $(MODULE_DIR)/target/tutorial-state
GRAPHRAG_TUTORIAL_PDF_PATH ?= src/main/resources/corpus/ingestion/pdf
GRAPHRAG_TUTORIAL_CORPUS_PATH ?= data/pdf-corpus.ttl
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
INDEX_DIR := $(WORK_DIR)/real-provider-index
FUSEKI_DISTRIBUTION_BASE := $(WORK_DIR)/fuseki-distribution
FUSEKI_DISTRIBUTION_CONFIG := $(FUSEKI_DISTRIBUTION_BASE)/service.ttl
FUSEKI_DISTRIBUTION_EXTRA := $(FUSEKI_DISTRIBUTION_BASE)/extra
FUSEKI_CONFIG_GENERATOR := $(MODULE_DIR)/scripts/graphrag-tutorial-fuseki-config.sh
MATERIALIZER_SOURCE := $(ROOT_DIR)/jena-graphrag/src/main/java/org/apache/jena/graphrag/ingestion/PdfCorpusMaterializer.java
ARQ_CLASS := $(ROOT_DIR)/jena-cmds/target/classes/arq/arq.class
GRAPHRAG_CLASSES := $(ROOT_DIR)/jena-graphrag/target/classes
DATASET := $(GRAPHRAG_TUTORIAL_DATASET)
CHAT_MODE_QUESTION ?= What is GraphRAG?
CHAT_MODE_REQUIRE_MIXED_LOCAL ?= false
CHAT_MODE_REQUIRE_MULTIPLE_GLOBAL ?= false
TOP_K ?= 1
GRAPHRAG_TUTORIAL_JAVA_OPTIONS ?=
GETTING_STARTED_QUESTION ?= What is GraphRAG?
DRIFT_LIMIT_COMMUNITY_TOP_K ?= 1
DRIFT_LIMIT_MAX_FOLLOW_UPS ?= 1
DRIFT_LIMIT_CONTEXT_TOKEN_BUDGET ?= 64
DRIFT_LIMIT_LOCAL_TOP_K ?= 1
export QUESTION MODE TOP_K
SYSTEM_PROMPT := Answer only from the ingested GraphRAG research PDF corpus. Cite retrieved sources and say when the corpus lacks the answer. When the sources mention GraphDB, Elasticsearch, LM Studio, or LangChain, identify them as components of the described RDF integration or corpus, not requirements of generic Microsoft GraphRAG.
FUSEKI_URL = http://127.0.0.1:$$(cat "$(PORT_FILE)")
WINDOWS_FUSEKI_URL = http://$$(hostname -I | awk '{print $$1}'):$$(cat "$(PORT_FILE)")

.PHONY: help providers-bootstrap jena-install jena-install-check fuseki-distribution-prepare pdfs-check project-prepare corpus-materialize corpus-validate corpus-load \
	corpus-semantic-extract fuseki-start fuseki-start-basic fuseki-start-server fuseki-ping fuseki-status fuseki-open graphrag-config indexing-start indexing-status \
	indexing-wait rdf-lifecycle corpus-verify-pdfs corpus-statistic graph-cat ttt-cat entities-list relationships-list communities-list findings-list chat-ask-questions chat-ask-modes chat-qualify-mode chat-qualify-basic-vector chat-qualify-drift chat-qualify-drift-limits chat-question context-question fuseki-stop tutorial-clean tutorial-run getting-started

help: ## Display tutorial steps
	@printf '%s\n' \
		'GraphRAG PDF tutorial' \
		'' \
		'Getting started' \
		'  getting-started           Run the minimal basic-mode workflow with real providers.' \
		'' \
		'Recommended sequence:' \
		'  make jena-install providers-bootstrap corpus-materialize corpus-semantic-extract' \
		'  make fuseki-start fuseki-ping corpus-load corpus-verify-pdfs indexing-start indexing-wait' \
		'' \
		'Preparation' \
		'  providers-bootstrap       Generate the local provider environment.' \
		'  jena-install              Build and install the checkout Jena version.' \
		'  jena-install-check        Verify the installed Jena distributions.' \
		'  fuseki-distribution-prepare Stage GraphRAG for the installed Fuseki and generate its service config.' \
		'  pdfs-check                Verify the configured PDF corpus.' \
		'  project-prepare           Compile the tutorial dependencies.' \
		'  corpus-validate           Validate the Turtle corpus with installed RIOT.' \
		'  tutorial-clean            Remove the temporary tutorial state.' \
		'' \
		'Corpus' \
		'  corpus-materialize        Ingest PDFs into the local Turtle corpus.' \
		'  corpus-load               Upload the local Turtle corpus through Fuseki HTTP.' \
		'  corpus-semantic-extract   Extract entities, relationships, and communities.' \
		'  corpus-statistic          Show local corpus and semantic graph counts.' \
		'  graph-cat                 Dump the local RDF graph page by page with Jena.' \
		'' \
		'Fuseki and indexing' \
		'  fuseki-start              Start installed Fuseki after semantic materialization; no upload.' \
		'  fuseki-start-basic        Start installed Fuseki; no corpus materialization or upload.' \
		'  fuseki-ping               Confirm that Fuseki is reachable.' \
		'  fuseki-status             Report Fuseki process and HTTP status.' \
		'  fuseki-open               Open the Fuseki UI.' \
		'  graphrag-config           Show the public GraphRAG configuration.' \
		'  indexing-start            Start vector indexing.' \
		'  indexing-status           Show vector indexing status.' \
		'  indexing-wait             Wait for vector indexing to finish.' \
		'  rdf-lifecycle             Exercise the isolated RDF import/update/delete/rebuild cycle.' \
		'' \
		'Validation and questions' \
		'  corpus-verify-pdfs        Verify the PDFs loaded in Fuseki.' \
		'  entities-list             List extracted entities from the local Turtle corpus.' \
		'  relationships-list        List extracted relationships from the local Turtle corpus.' \
		'  communities-list          List extracted communities.' \
		'  findings-list             List extracted findings from the local Turtle corpus.' \
		'  chat-ask-questions        Run the five tutorial questions.' \
		'  chat-ask-modes            Qualify basic, local, and global with real providers.' \
		'  chat-qualify-mode         Qualify context and citations: QUESTION="..." MODE=basic TOP_K=1.' \
		'  chat-qualify-drift        Qualify the bounded DRIFT traversal with real providers.' \
		'  chat-qualify-drift-limits Qualify DRIFT with non-default runtime limits and real providers.' \
		'  chat-question             Ask one question: make chat-question QUESTION="..." MODE=local' \
		'  context-question          Inspect context: make context-question QUESTION="..." MODE=local' \
		'' \
		'All steps' \
		'  tutorial-run              Run the complete tutorial workflow.' \
		'  fuseki-stop               Stop the Fuseki server.'

providers-bootstrap: ## Generate the ignored real-provider environment
	@$(MODULE_DIR)/scripts/graphrag-tutorial-provider-bootstrap.sh

jena-install: providers-bootstrap ## Build and install the checkout Jena version
	@set -euo pipefail; \
	env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	set -a; source "$$env_file"; set +a; \
	[[ "$$JENA_INSTALL_DIR" == /* ]] || { echo "JENA_INSTALL_DIR must be an absolute path." >&2; exit 1; }; \
	jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	for directory in \
		"$$JENA_INSTALL_DIR/apache-jena-$$jena_version" \
		"$$JENA_INSTALL_DIR/apache-jena-fuseki-$$jena_version" \
		"$$JENA_INSTALL_DIR/graphrag"; do \
		[[ ! -e "$$directory" ]] || { echo "Installation already exists: $$directory" >&2; exit 1; }; \
	done; \
	echo "Installing Jena $$jena_version in $$JENA_INSTALL_DIR"; \
	mkdir -p "$$JENA_INSTALL_DIR"; \
	cd "$(ROOT_DIR)" && mvn -Drat.skip=true -Pcomplete,graphrag clean package -DskipTests; \
	tar --extract --gzip --file "$(ROOT_DIR)/apache-jena/target/apache-jena-$$jena_version.tar.gz" --directory "$$JENA_INSTALL_DIR"; \
	tar --extract --gzip --file "$(ROOT_DIR)/jena-fuseki2/apache-jena-fuseki/target/apache-jena-fuseki-$$jena_version.tar.gz" --directory "$$JENA_INSTALL_DIR"; \
	mkdir "$$JENA_INSTALL_DIR/graphrag"; \
	cp "$(ROOT_DIR)/jena-graphrag/target/jena-graphrag-$$jena_version.jar" "$$JENA_INSTALL_DIR/graphrag/"; \
	cd "$(ROOT_DIR)" && mvn -q -Pcomplete,graphrag -pl jena-graphrag dependency:copy-dependencies \
		-DincludeScope=runtime -DoutputDirectory="$$JENA_INSTALL_DIR/graphrag"

jena-install-check: providers-bootstrap ## Verify the installed Jena distributions
	@set -euo pipefail; \
	env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	set -a; source "$$env_file"; set +a; \
	jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	jena_home="$$JENA_INSTALL_DIR/apache-jena-$$jena_version"; \
	fuseki_home="$$JENA_INSTALL_DIR/apache-jena-fuseki-$$jena_version"; \
	JENA_HOME="$$jena_home" "$$jena_home/bin/sparql" --version; \
	JENA_HOME="$$jena_home" "$$jena_home/bin/riot" --version; \
	FUSEKI_HOME="$$fuseki_home" "$$fuseki_home/fuseki-server" --version >/dev/null; \
	test -f "$$JENA_INSTALL_DIR/graphrag/jena-graphrag-$$jena_version.jar"; \
	runtime_jars=$$(find "$$JENA_INSTALL_DIR/graphrag" -maxdepth 1 -type f -name '*.jar' | wc -l); \
	[[ "$$runtime_jars" -gt 1 ]] || { echo "GraphRAG runtime dependencies are unavailable." >&2; exit 1; }; \
	echo "Jena $$jena_version is installed in $$JENA_INSTALL_DIR."

fuseki-distribution-prepare: jena-install-check $(PORT_FILE) ## Stage GraphRAG for the installed Fuseki and generate its service config
	@set -euo pipefail; \
	env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	set -a; source "$$env_file"; set +a; \
	jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	fuseki_home="$$JENA_INSTALL_DIR/apache-jena-fuseki-$$jena_version"; \
	graphrag_home="$$JENA_INSTALL_DIR/graphrag"; \
	[[ -x "$$fuseki_home/fuseki-server" ]] || { echo "Installed Fuseki launcher is unavailable: $$fuseki_home/fuseki-server" >&2; exit 1; }; \
	shopt -s nullglob; jars=("$$graphrag_home"/*.jar); \
	(( $${#jars[@]} > 0 )) || { echo "Installed GraphRAG runtime jars are unavailable: $$graphrag_home" >&2; exit 1; }; \
	mkdir -p "$(FUSEKI_DISTRIBUTION_EXTRA)"; \
	cp "$${jars[@]}" "$(FUSEKI_DISTRIBUTION_EXTRA)/"; \
	vector_dimension="$${GRAPHRAG_EMBEDDING_DIMENSION:-384}"; \
	"$(FUSEKI_CONFIG_GENERATOR)" "$(FUSEKI_DISTRIBUTION_CONFIG)" "$(FUSEKI_DISTRIBUTION_BASE)" \
		"$(DATASET)" "$$(cat "$(PORT_FILE)")" "$(INDEX_DIR)" "$$vector_dimension"; \
	echo "Installed Fuseki runtime staged in $(FUSEKI_DISTRIBUTION_BASE)."; \
	echo "Service configuration: $(FUSEKI_DISTRIBUTION_CONFIG)"

pdfs-check: ## Verify the configured PDF fixture count
	@count=$$(find "$(PDF_DIR)" -maxdepth 1 -type f -iname '*.pdf' | wc -l); \
	[[ "$$count" == "$(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT)" ]] || { echo "Expected $(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT) PDF fixtures, found $$count." >&2; exit 1; }; \
	echo "$$count PDF fixture(s) found."

$(CLASSPATH_FILE): $(MATERIALIZER_SOURCE)
	@mkdir -p "$(WORK_DIR)"
	@cd "$(ROOT_DIR)" && mvn -q -Drat.skip=true -Pgraphrag -pl jena-graphrag-tutorial -am package -DskipTests
	@cd "$(ROOT_DIR)" && mvn -q -Pgraphrag -pl jena-graphrag-tutorial dependency:build-classpath \
		-DincludeScope=runtime -Dmdep.outputFile="$(CLASSPATH_FILE)"

project-prepare: $(CLASSPATH_FILE) ## Compile the module and create its runtime classpath
	@echo "Classpath prepared in $(CLASSPATH_FILE)."

$(ARQ_CLASS):
	@cd "$(ROOT_DIR)" && mvn -q -Drat.skip=true -pl jena-cmds -am package -DskipTests

$(CORPUS_FILE): $(MODULE_DIR)/env/generated/real-providers.mk $(CLASSPATH_FILE) $(MATERIALIZER_SOURCE)
	@mkdir -p "$$(dirname "$(CORPUS_FILE)")"
	@rm -f "$(SEMANTIC_MARKER)"
	@java -cp "$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
		org.apache.jena.graphrag.ingestion.PdfCorpusMaterializer "$(PDF_DIR)" "$@"
	@test -s "$@"

corpus-materialize: pdfs-check $(CORPUS_FILE) corpus-validate ## Materialize the PDFs through DocumentIngestionService
	@echo "PDF corpus materialized at $(CORPUS_FILE)."

corpus-validate: jena-install-check $(CORPUS_FILE) ## Validate the Turtle corpus with the installed RIOT command
	@set -euo pipefail; \
	env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	set -a; source "$$env_file"; set +a; \
	jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	jena_home="$$JENA_INSTALL_DIR/apache-jena-$$jena_version"; \
	JENA_HOME="$$jena_home" "$$jena_home/bin/riot" --validate "$(CORPUS_FILE)" >/dev/null; \
	echo "Turtle corpus validated by installed RIOT."

corpus-load: fuseki-ping ## Upload a materialized Turtle corpus through Fuseki Graph Store HTTP
	@test -s "$(CORPUS_FILE)" || { echo "Corpus unavailable: run make corpus-materialize first." >&2; exit 1; }
	@curl --fail --silent --show-error \
		-X PUT -H 'Content-Type: text/turtle' --upload-file "$(CORPUS_FILE)" \
		"$(FUSEKI_URL)/$(DATASET)/data?default" >/dev/null
	@echo "Turtle corpus uploaded to the persistent Fuseki dataset."

$(SEMANTIC_MARKER): $(CORPUS_FILE) $(CLASSPATH_FILE) $(MATERIALIZER_SOURCE)
	@env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	[[ -r "$$env_file" ]] || { echo "Run make providers-bootstrap first." >&2; exit 1; }; \
	set -a; source "$$env_file"; set +a; \
	java -cp "$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
		org.apache.jena.graphrag.ingestion.PdfCorpusMaterializer "$(PDF_DIR)" "$(CORPUS_FILE)" --semantic "$(SEMANTIC_CHECKPOINT)" && \
		touch "$@"

corpus-semantic-extract: pdfs-check $(SEMANTIC_MARKER) ## Extract entities, relationships, and communities from the PDF chunks
	@echo "Semantic graph materialized in $(CORPUS_FILE)."

$(PORT_FILE):
	@mkdir -p "$(WORK_DIR)"
	@node -e 'const server = require("net").createServer(); server.listen(0, "127.0.0.1", () => { console.log(server.address().port); server.close(); });' > "$@"

fuseki-start: fuseki-distribution-prepare ## Start the installed Fuseki, ready for an enriched corpus upload
	@test -f "$(SEMANTIC_MARKER)" || { echo "Semantic corpus unavailable: run make corpus-semantic-extract first." >&2; exit 1; }
	@$(MAKE) fuseki-start-server

fuseki-start-basic: fuseki-distribution-prepare ## Start the installed Fuseki without loading a corpus
	@$(MAKE) fuseki-start-server

fuseki-start-server:
	@if [[ -f "$(PID_FILE)" ]] && kill -0 "$$(cat "$(PID_FILE)")" 2>/dev/null; then \
		echo "Fuseki is already running at $(FUSEKI_URL)."; \
	else \
		env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
		[[ -r "$$env_file" ]] || { echo "Run make bootstrap first." >&2; exit 1; }; \
		set -a; source "$$env_file"; set +a; \
		jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
		fuseki_home="$$JENA_INSTALL_DIR/apache-jena-fuseki-$$jena_version"; \
		GRAPHRAG_SYSTEM_PROMPT='$(SYSTEM_PROMPT)' \
		FUSEKI_HOME="$$fuseki_home" FUSEKI_BASE="$(FUSEKI_DISTRIBUTION_BASE)" \
		JVM_ARGS="-Xmx4G $(GRAPHRAG_TUTORIAL_JAVA_OPTIONS)" \
		nohup "$$fuseki_home/fuseki-server" --port="$$(cat "$(PORT_FILE)")" --config="$(FUSEKI_DISTRIBUTION_CONFIG)" \
			> "$(LOG_FILE)" 2>&1 < /dev/null & echo $$! > "$(PID_FILE)"; \
		echo "Fuseki started at $(FUSEKI_URL)."; \
		echo "Log: $(LOG_FILE)"; \
	fi

fuseki-ping: ## Confirm Fuseki is reachable
	@for attempt in $$(seq 1 30); do \
		if curl --fail --silent --show-error "$(FUSEKI_URL)/\$$/ping" >/dev/null 2>&1; then exit 0; fi; \
		if [[ $$attempt -eq 30 ]]; then \
			echo "Fuseki did not respond to ping at $(FUSEKI_URL)." >&2; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	@echo "Fuseki responds to ping."

fuseki-status: ## Report Fuseki process, ping, and dataset status
	@port=unknown; [[ -s "$(PORT_FILE)" ]] && port=$$(cat "$(PORT_FILE)"); \
	pid=unknown; [[ -s "$(PID_FILE)" ]] && pid=$$(cat "$(PID_FILE)"); \
	echo "Fuseki status"; \
	echo "PID: $$pid"; \
	echo "URL: http://127.0.0.1:$$port"; \
	echo "Dataset: $(DATASET)"; \
	echo "Configuration: $(FUSEKI_DISTRIBUTION_CONFIG)"; \
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

rdf-lifecycle: fuseki-ping ## Exercise isolated RDF import, read, update, deletion, and index reconstruction
	@set -e; \
	base="$(FUSEKI_URL)/$(DATASET)"; \
	document='urn:graphrag:tutorial:rdf-lifecycle:document'; \
	chunk='urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0'; \
	count() { \
		curl --fail --silent --show-error -G "$$base/sparql" \
			--data-urlencode "query=$$1" \
			--data-urlencode 'format=application/sparql-results+json' | \
			node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).results.bindings[0].count.value));'; \
	}; \
	update() { \
		curl --fail --silent --show-error -X POST "$$base/update" --data-urlencode "update=$$1" >/dev/null; \
	}; \
	update "PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> INSERT DATA { <$$document> a grag:Document ; grag:id \"rdf-lifecycle-document\" ; grag:title \"RDF lifecycle tutorial document\" . <$$chunk> a grag:Chunk ; grag:id \"rdf-lifecycle-chunk-0\" ; grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" ; grag:partOf <$$document> . }"; \
	loaded=$$(count "PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(DISTINCT ?resource) AS ?count) WHERE { VALUES ?resource { <$$document> <$$chunk> } ?resource a ?type }"); \
	[[ "$$loaded" == 2 ]] || { echo "Expected the tutorial document and chunk after import, found $$loaded resources." >&2; exit 1; }; \
	echo "RDF import verified: document and chunk are present in the default graph."
	@$(MAKE) indexing-start indexing-wait
	@set -e; \
	base="$(FUSEKI_URL)/$(DATASET)"; \
	document='urn:graphrag:tutorial:rdf-lifecycle:document'; \
	chunk='urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0'; \
	update() { curl --fail --silent --show-error -X POST "$$base/update" --data-urlencode "update=$$1" >/dev/null; }; \
	count() { curl --fail --silent --show-error -G "$$base/sparql" --data-urlencode "query=$$1" --data-urlencode 'format=application/sparql-results+json' | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).results.bindings[0].count.value));'; }; \
	update "PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> DELETE { <$$chunk> grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" } INSERT { <$$chunk> grag:text \"RDF lifecycle revised marker: the RDF graph is the source of truth.\" } WHERE { <$$chunk> grag:text \"RDF lifecycle original marker: import, read, update, and delete.\" }"; \
	revised=$$(count "PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(*) AS ?count) WHERE { <$$chunk> grag:text \"RDF lifecycle revised marker: the RDF graph is the source of truth.\" }"); \
	[[ "$$revised" == 1 ]] || { echo "Expected the revised chunk text after update." >&2; exit 1; }; \
	echo "RDF update verified: the targeted chunk text changed."
	@$(MAKE) indexing-start indexing-wait
	@set -e; \
	base="$(FUSEKI_URL)/$(DATASET)"; \
	document='urn:graphrag:tutorial:rdf-lifecycle:document'; \
	chunk='urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0'; \
	curl --fail --silent --show-error -X POST "$$base/update" --data-urlencode "update=DELETE { <$$document> ?documentPredicate ?documentObject . <$$chunk> ?chunkPredicate ?chunkObject } WHERE { OPTIONAL { <$$document> ?documentPredicate ?documentObject } OPTIONAL { <$$chunk> ?chunkPredicate ?chunkObject } }" >/dev/null; \
	remaining=$$(curl --fail --silent --show-error -G "$$base/sparql" --data-urlencode "query=PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(*) AS ?count) WHERE { VALUES ?resource { <$$document> <$$chunk> } ?resource ?predicate ?object }" --data-urlencode 'format=application/sparql-results+json' | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).results.bindings[0].count.value));'); \
	[[ "$$remaining" == 0 ]] || { echo "Expected no lifecycle RDF triples after deletion, found $$remaining." >&2; exit 1; }; \
	echo "RDF deletion verified: the targeted document and chunk are absent."
	@$(MAKE) fuseki-stop
	@rm -rf "$(INDEX_DIR)"
	@rm -f "$(TASK_FILE)" "$(WORK_DIR)/index-response.json"
	@$(MAKE) fuseki-start-basic fuseki-ping indexing-start indexing-wait
	@set -e; \
	base="$(FUSEKI_URL)/$(DATASET)"; \
	remaining=$$(curl --fail --silent --show-error -G "$$base/sparql" --data-urlencode 'query=PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(*) AS ?count) WHERE { VALUES ?resource { <urn:graphrag:tutorial:rdf-lifecycle:document> <urn:graphrag:tutorial:rdf-lifecycle:document#chunk-0> } ?resource ?predicate ?object }' --data-urlencode 'format=application/sparql-results+json' | node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => console.log(JSON.parse(input).results.bindings[0].count.value));'); \
	[[ "$$remaining" == 0 ]] || { echo "The reconstructed tutorial dataset still contains lifecycle RDF." >&2; exit 1; }; \
	echo "Clean tutorial state restored: the temporary vector index was rebuilt from the original corpus."

corpus-verify-pdfs: fuseki-ping jena-install-check ## Verify the loaded PDFs and chunks with the installed SPARQL command
	@set -euo pipefail; \
	env_file="$(MODULE_DIR)/env/generated/real-providers.env.sh"; \
	set -a; source "$$env_file"; set +a; \
	jena_version="$$(cd "$(ROOT_DIR)" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"; \
	jena_home="$$JENA_INSTALL_DIR/apache-jena-$$jena_version"; \
	service="$(FUSEKI_URL)/$(DATASET)/sparql"; \
	response=$$(printf '%s\n' "PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT (COUNT(DISTINCT ?document) AS ?pdfDocuments) (COUNT(DISTINCT ?chunk) AS ?chunks) WHERE { SERVICE <$$service> { OPTIONAL { ?document a grag:Document ; grag:sourceFile ?sourceFile . FILTER(STRENDS(LCASE(STR(?sourceFile)), \".pdf\")) } OPTIONAL { ?chunk a grag:Chunk } } }" | JENA_HOME="$$jena_home" "$$jena_home/bin/sparql" --query=- --results=JSON); \
	RESPONSE="$$response" EXPECTED="$(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT)" node -e 'const row = JSON.parse(process.env.RESPONSE).results.bindings[0] ?? {}; const pdfs = Number(row.pdfDocuments?.value ?? 0); const chunks = Number(row.chunks?.value ?? 0); if (pdfs !== Number(process.env.EXPECTED) || chunks < 1) process.exit(1); console.log(pdfs + " PDF document(s) and " + chunks + " chunk(s) verified with installed SPARQL.");' || { echo "Expected $(GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT) PDF documents and at least one chunk." >&2; exit 1; }

corpus-statistic: pdfs-check $(CORPUS_FILE) $(ARQ_CLASS) ## Display GraphRAG resource and structural-link statistics
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?triples ?documents ?pdfDocuments ?chunks ?entities ?relationships ?communities ?findings ?partOfLinks ?entityLinks ?relatedToLinks ?communityLinks WHERE { { SELECT (COUNT(*) AS ?triples) WHERE { ?subject ?predicate ?object } } { SELECT (COUNT(DISTINCT ?document) AS ?documents) WHERE { ?document a grag:Document } } { SELECT (COUNT(DISTINCT ?pdfDocument) AS ?pdfDocuments) WHERE { ?pdfDocument a grag:Document ; grag:sourceFile ?sourceFile . FILTER(STRENDS(LCASE(STR(?sourceFile)), ".pdf")) } } { SELECT (COUNT(DISTINCT ?chunk) AS ?chunks) WHERE { ?chunk a grag:Chunk } } { SELECT (COUNT(DISTINCT ?entity) AS ?entities) WHERE { ?entity a grag:Entity } } { SELECT (COUNT(DISTINCT ?relationship) AS ?relationships) WHERE { ?relationship a grag:Relationship } } { SELECT (COUNT(DISTINCT ?community) AS ?communities) WHERE { ?community a grag:Community } } { SELECT (COUNT(DISTINCT ?finding) AS ?findings) WHERE { ?finding a grag:Finding } } { SELECT (COUNT(*) AS ?partOfLinks) WHERE { ?chunk grag:partOf ?document } } { SELECT (COUNT(*) AS ?entityLinks) WHERE { ?chunk grag:hasEntity ?entity } } { SELECT (COUNT(*) AS ?relatedToLinks) WHERE { ?source grag:relatedTo ?target } } { SELECT (COUNT(*) AS ?communityLinks) WHERE { ?entity grag:inCommunity ?community } } }' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const row = JSON.parse(input).results.bindings[0]; const count = name => Number(row[name]?.value ?? 0).toLocaleString("en-US"); const line = (label, name) => console.log("  " + label.padEnd(36) + count(name)); console.log("GraphRAG corpus statistics"); console.log(""); console.log("Resources"); line("Triples", "triples"); line("Documents", "documents"); line("PDF documents", "pdfDocuments"); line("Chunks", "chunks"); console.log(""); console.log("Semantic enrichment"); line("Entities", "entities"); line("Relationships", "relationships"); line("Communities", "communities"); line("Findings", "findings"); console.log(""); console.log("Structural links"); line("Chunk -> document (partOf)", "partOfLinks"); line("Chunk -> entity (hasEntity)", "entityLinks"); line("Entity -> entity (relatedTo)", "relatedToLinks"); line("Entity -> community (inCommunity)", "communityLinks"); });'

graph-cat: $(CORPUS_FILE) $(CLASSPATH_FILE) $(ARQ_CLASS) ## Display the RDF graph content page by page with the Jena command
	@echo "Graph content for $(CORPUS_FILE)"
	@java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
		jena.riot --output=TURTLE "$(CORPUS_FILE)" | more

entities-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG entities from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?entity ?name WHERE { ?entity a grag:Entity ; grag:name ?name } ORDER BY LCASE(STR(?name))' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG entities found."); process.exit(0); } for (const entity of bindings) console.log(entity.name.value + "\n  " + entity.entity.value); });'

relationships-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG relationships from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?sourceName ?description ?targetName WHERE { ?relationship a grag:Relationship ; grag:source ?source ; grag:target ?target . ?source grag:name ?sourceName . ?target grag:name ?targetName . OPTIONAL { ?relationship grag:description ?description } } ORDER BY LCASE(STR(?sourceName)) LCASE(STR(?targetName))' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG relationships found."); process.exit(0); } for (const relationship of bindings) console.log(relationship.sourceName.value + " --" + (relationship.description?.value ?? "related to") + "--> " + relationship.targetName.value); });'

communities-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG communities and their available summaries locally
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?community ?title ?summary ?fullContent WHERE { ?community a grag:Community . OPTIONAL { ?community grag:title ?title } OPTIONAL { ?community grag:summary ?summary } OPTIONAL { ?community grag:fullContent ?fullContent } } ORDER BY STR(?community)' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
			arq.arq --data "$(CORPUS_FILE)" --query=- --results=JSON | \
		node -e 'let input = ""; process.stdin.on("data", chunk => input += chunk).on("end", () => { const bindings = JSON.parse(input).results.bindings; if (bindings.length === 0) { console.log("No GraphRAG communities found."); process.exit(0); } for (const community of bindings) { console.log("Community: " + community.community.value); if (community.title) console.log("Title: " + community.title.value); if (community.summary) console.log("Summary: " + community.summary.value); if (community.fullContent) console.log("Full content: " + community.fullContent.value); console.log(""); } });'

findings-list: $(CORPUS_FILE) $(ARQ_CLASS) ## List GraphRAG findings from the local Turtle corpus
	@printf '%s\n' 'PREFIX grag: <http://ormynet.com/ns/msft-graphrag#> SELECT ?finding ?summary ?fullContent WHERE { ?finding a grag:Finding . OPTIONAL { ?finding grag:summary ?summary } OPTIONAL { ?finding grag:fullContent ?fullContent } } ORDER BY STR(?finding)' | \
		java -cp "$(ARQ_CLASS:%/arq/arq.class=%):$(MODULE_DIR)/target/classes:$(GRAPHRAG_CLASSES):$$(cat "$(CLASSPATH_FILE)")" \
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
		CONTEXT="$$context" RESPONSE="$$response" MODE="$$mode" REQUIRE_MIXED_LOCAL="$(CHAT_MODE_REQUIRE_MIXED_LOCAL)" REQUIRE_MULTIPLE_GLOBAL="$(CHAT_MODE_REQUIRE_MULTIPLE_GLOBAL)" node -e 'const context = JSON.parse(process.env.CONTEXT); const response = JSON.parse(process.env.RESPONSE); const expected = context.results.map(result => [result.uri, result.sourceText]); const citations = (response.citations ?? []).map(citation => [citation.uri, citation.text]); const abstention = "Aucun contexte GraphRAG correspondant a la question."; const allowedTypes = {basic: ["chunk"], local: ["relationship", "entity", "chunk", "community"], global: ["community"]}; const integrationComponent = /\b(graphdb|elasticsearch|lm studio|langchain)\b/i; const integrationScope = /\b(corpus|document|article|partie\s+3|demonstration|integration\s+rdf|solution\s+decrite)\b/i; const genericIntegrationClaim = /\bmicrosoft\s+graphrag\b.{0,100}\b(uses|utilizes|integrates|requires|contains|utilise|integre|requiert|contient)\b.{0,120}\b(graphdb|elasticsearch|lm studio|langchain)\b/i; if (!response.answer || !response.answer.trim()) process.exit(1); if (response.answer === abstention) { if (expected.length !== 0 || citations.length !== 0) process.exit(1); console.log("Status: abstention (no matching mode context)."); } else { const types = [...new Set(context.results.map(result => result.type))]; if (expected.length === 0 || JSON.stringify(citations) !== JSON.stringify(expected) || types.some(type => !allowedTypes[process.env.MODE].includes(type))) process.exit(1); if (genericIntegrationClaim.test(response.answer) || integrationComponent.test(response.answer) && !integrationScope.test(response.answer)) { console.error("Qualification failure: RDF integration components must be scoped to the retrieved corpus or described integration."); process.exit(1); } console.log("Status: cited answer."); console.log("Context types: " + types.join(", ")); if (process.env.MODE === "local" && types.length < 2) { if (process.env.REQUIRE_MIXED_LOCAL === "true") process.exit(1); console.log("Qualification limit: local context is not mixed for this corpus and question."); } if (process.env.MODE === "global" && context.results.length < 2) { if (process.env.REQUIRE_MULTIPLE_GLOBAL === "true") process.exit(1); console.log("Qualification limit: global context has fewer than two community reports."); } console.log("Answer:\n" + response.answer.trim()); console.log("Mode citations:"); for (const citation of citations) console.log("  " + citation[0]); }'; \
		echo; \
	done

chat-qualify-mode: ## Qualify a mode context and answer with QUESTION, MODE and TOP_K options
	@set -e; \
	question="$${QUESTION:-What is GraphRAG?}"; \
	mode="$${MODE:-basic}"; \
	top_k="$${TOP_K:-1}"; \
	[[ "$$top_k" =~ ^[1-9][0-9]*$$ && "$$top_k" -le 100 ]] || { echo 'TOP_K must be an integer from 1 to 100.' >&2; exit 1; }; \
	if [[ "$$mode" == drift ]]; then \
		$(MAKE) chat-qualify-drift QUESTION="$$question" TOP_K="$$top_k"; \
		exit $$?; \
	fi; \
	[[ "$$mode" == basic || "$$mode" == local || "$$mode" == global ]] || { echo 'MODE must be basic, local, global, or drift.' >&2; exit 1; }; \
	echo "Question: $$question"; \
	echo "Mode: $$mode"; \
	echo "Top K: $$top_k"; \
	context=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/context" --data-urlencode "q=$$question" --data-urlencode "mode=$$mode" --data-urlencode "topK=$$top_k"); \
	response=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$$question" --data-urlencode "mode=$$mode" --data-urlencode "topK=$$top_k"); \
	CONTEXT="$$context" RESPONSE="$$response" MODE="$$mode" TOP_K="$$top_k" node -e 'const context = JSON.parse(process.env.CONTEXT); const response = JSON.parse(process.env.RESPONSE); const mode = process.env.MODE; const topK = Number(process.env.TOP_K); const abstention = "Aucun contexte GraphRAG correspondant a la question."; const allowedTypes = {basic: ["chunk"], local: ["relationship", "entity", "chunk", "community"], global: ["community"]}; const results = context.results ?? []; const citations = response.citations ?? []; const expected = results.map(result => [result.uri, result.sourceText]); const actual = citations.map(citation => [citation.uri, citation.text]); if (context.mode !== mode || results.length > topK || results.some(result => !allowedTypes[mode].includes(result.type)) || !response.answer?.trim()) process.exit(1); if (response.answer === abstention) { if (results.length !== 0 || citations.length !== 0) process.exit(1); console.log("Qualification passed: abstention without context."); process.exit(0); } if (results.length === 0 || JSON.stringify(actual) !== JSON.stringify(expected)) process.exit(1); console.log("Mode qualification passed."); console.log("Context resources: " + results.length); console.log("Answer: " + response.answer.trim());';

chat-qualify-basic-vector: chat-qualify-mode ## Backward-compatible alias for basic vector qualification

chat-qualify-drift: ## Qualify the DRIFT primer, citations, and traversal metadata
	@set -e; \
	question="$${QUESTION:-What is GraphRAG?}"; \
	top_k="$${TOP_K:-1}"; \
	[[ "$$top_k" =~ ^[1-9][0-9]*$$ && "$$top_k" -le 100 ]] || { echo 'TOP_K must be an integer from 1 to 100.' >&2; exit 1; }; \
	echo "Question: $$question"; \
	echo "Mode: drift"; \
	echo "Top K: $$top_k"; \
	context=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/context" --data-urlencode "q=$$question" --data-urlencode 'mode=drift' --data-urlencode "topK=$$top_k"); \
	response=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$$question" --data-urlencode 'mode=drift' --data-urlencode "topK=$$top_k"); \
	CONTEXT="$$context" RESPONSE="$$response" TOP_K="$$top_k" node -e 'const context = JSON.parse(process.env.CONTEXT); const response = JSON.parse(process.env.RESPONSE); const topK = Number(process.env.TOP_K); const abstention = "Aucun contexte GraphRAG correspondant a la question."; const results = context.results ?? []; const citations = response.citations ?? []; const expected = results.map(result => [result.uri, result.sourceText]); const actual = citations.map(citation => [citation.uri, citation.text]); const matchesPrimer = JSON.stringify(actual.slice(0, expected.length)) === JSON.stringify(expected); if (context.mode !== "drift" || results.length > topK || results.some(result => result.type !== "community") || !response.answer?.trim()) process.exit(1); if (response.answer === abstention) { if (results.length !== 0 || citations.length !== 0) process.exit(1); console.log("DRIFT qualification passed: abstention without primer."); console.log("Answer: " + response.answer.trim()); process.exit(0); } if (results.length === 0 || !matchesPrimer || !response.reasonStop || !Number.isInteger(response.followUpCount) || response.followUpCount < 0) process.exit(1); console.log("DRIFT qualification passed."); console.log("Primer communities: " + results.length); console.log("Stop reason: " + response.reasonStop); console.log("Follow-ups: " + response.followUpCount); console.log("Answer:\n" + response.answer.trim());';

chat-qualify-drift-limits: ## Restart Fuseki with non-default DRIFT limits and qualify them with real providers
	@set -e; \
	$(MAKE) fuseki-stop; \
	$(MAKE) fuseki-start GRAPHRAG_TUTORIAL_JAVA_OPTIONS="-Djena.graphrag.drift.communityTopK=$(DRIFT_LIMIT_COMMUNITY_TOP_K) -Djena.graphrag.drift.maxFollowUps=$(DRIFT_LIMIT_MAX_FOLLOW_UPS) -Djena.graphrag.drift.contextTokenBudget=$(DRIFT_LIMIT_CONTEXT_TOKEN_BUDGET) -Djena.graphrag.drift.localTopK=$(DRIFT_LIMIT_LOCAL_TOP_K)"; \
	$(MAKE) fuseki-ping indexing-start indexing-wait; \
	question="$${QUESTION:-What is GraphRAG?}"; \
	config=$$(curl --fail --silent --show-error "$(FUSEKI_URL)/$(DATASET)/graphrag/config"); \
	context=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/context" --data-urlencode "q=$$question" --data-urlencode 'mode=drift' --data-urlencode 'topK=100'); \
	response=$$(curl --fail --silent --show-error -G "$(FUSEKI_URL)/$(DATASET)/graphrag/answer" --data-urlencode "q=$$question" --data-urlencode 'mode=drift' --data-urlencode 'topK=100'); \
	CONFIG="$$config" CONTEXT="$$context" RESPONSE="$$response" EXPECTED_COMMUNITY_TOP_K="$(DRIFT_LIMIT_COMMUNITY_TOP_K)" EXPECTED_MAX_FOLLOW_UPS="$(DRIFT_LIMIT_MAX_FOLLOW_UPS)" EXPECTED_CONTEXT_TOKEN_BUDGET="$(DRIFT_LIMIT_CONTEXT_TOKEN_BUDGET)" EXPECTED_LOCAL_TOP_K="$(DRIFT_LIMIT_LOCAL_TOP_K)" node -e 'const config = JSON.parse(process.env.CONFIG); const context = JSON.parse(process.env.CONTEXT); const response = JSON.parse(process.env.RESPONSE); const limits = config.driftLimits ?? {}; const expected = { communityTopK: Number(process.env.EXPECTED_COMMUNITY_TOP_K), maxFollowUps: Number(process.env.EXPECTED_MAX_FOLLOW_UPS), contextTokenBudget: Number(process.env.EXPECTED_CONTEXT_TOKEN_BUDGET), localTopK: Number(process.env.EXPECTED_LOCAL_TOP_K) }; const results = context.results ?? []; const words = results.reduce((count, result) => count + (result.sourceText?.trim() ? result.sourceText.trim().split(/\s+/).length : 0), 0); const primer = results.map(result => [result.uri, result.sourceText]); const citations = (response.citations ?? []).map(citation => [citation.uri, citation.text]); const abstention = "Aucun contexte GraphRAG correspondant a la question."; if (Object.keys(expected).some(key => limits[key] !== expected[key]) || context.mode !== "drift" || results.length > limits.communityTopK || words > limits.contextTokenBudget || !response.answer?.trim()) process.exit(1); if (response.answer === abstention) { if (results.length !== 0 || citations.length !== 0) process.exit(1); console.log("DRIFT limit qualification passed: abstention without primer."); process.exit(0); } if (results.length === 0 || JSON.stringify(citations.slice(0, primer.length)) !== JSON.stringify(primer) || !Number.isInteger(response.followUpCount) || response.followUpCount < 0 || response.followUpCount > limits.maxFollowUps) process.exit(1); console.log("DRIFT limit qualification passed."); console.log("Limits: " + JSON.stringify(limits)); console.log("Primer communities: " + results.length); console.log("Primer tokens: " + words); console.log("Follow-ups: " + response.followUpCount);';

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
	CONTEXT="$$context" METRICS="$$metrics" MODE="$(MODE)" node -e 'const context = JSON.parse(process.env.CONTEXT); const metrics = JSON.parse(process.env.METRICS).results.bindings[0]; const count = name => metrics[name]?.value ?? "0"; console.log(JSON.stringify(context, null, 2)); if (context.results.length !== 0) process.exit(0); console.log("No context result."); console.log("Graph entities: " + count("entities") + ", relationships: " + count("relationships") + ", communities: " + count("communities") + "."); if (process.env.MODE === "basic") console.log("basic requires vector-indexed chunks when a GraphRAG index is configured; otherwise it uses the text or literal fallback."); if (process.env.MODE === "local") console.log("local requires GraphRAG entities and relationships; PDF ingestion alone creates documents and chunks only."); if (process.env.MODE === "global") console.log("global requires indexed GraphRAG communities with summary or fullContent values.");'

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

getting-started: providers-bootstrap pdfs-check project-prepare corpus-materialize fuseki-start-basic fuseki-ping corpus-load corpus-verify-pdfs graphrag-config indexing-start indexing-wait ## Run the minimal indexed basic-mode workflow
	@$(MAKE) chat-question QUESTION="$(GETTING_STARTED_QUESTION)" MODE=basic
	@echo "Getting started completed. Run make fuseki-stop when you are finished with Fuseki."

tutorial-run: providers-bootstrap pdfs-check project-prepare corpus-materialize corpus-semantic-extract fuseki-start fuseki-ping corpus-load corpus-verify-pdfs graphrag-config indexing-start indexing-wait chat-ask-questions chat-qualify-mode ## Run every tutorial step in sequence
	@echo "Tutorial completed successfully. Run make fuseki-stop when you are finished with Fuseki."
