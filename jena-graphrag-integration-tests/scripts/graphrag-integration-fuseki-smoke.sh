#!/usr/bin/env bash
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

set -euo pipefail

module_dir=$(cd "$(dirname "$0")/.." && pwd)
root_dir=$(cd "$module_dir/.." && pwd)
runtime_dir=$(mktemp -d)
log_file="$runtime_dir/fuseki.log"
dataset='graphrag-smoke'
port=$(node -e 'const server = require("net").createServer(); server.listen(0, "127.0.0.1", () => { console.log(server.address().port); server.close(); });')
fuseki_pid=''
playwright_script='test:smoke'
server_arguments=()
runtime_properties=()
enable_graphrag=true

cleanup() {
    status=$?
    trap - EXIT INT TERM
    if [[ -n "$fuseki_pid" ]]; then
        kill "$fuseki_pid" 2>/dev/null || true
        wait "$fuseki_pid" 2>/dev/null || true
    fi
    if [[ "$status" -ne 0 && -f "$log_file" ]]; then
        mkdir -p "$module_dir/target/playwright"
        cp "$log_file" "$module_dir/target/playwright/fuseki.log"
    else
        rm -rf "$runtime_dir"
    fi
    exit "$status"
}
trap cleanup EXIT INT TERM

cd "$root_dir"
mvn -q -Drat.skip=true -Pgraphrag -pl jena-graphrag-integration-tests -am package -DskipTests
mvn -q -Pgraphrag -pl jena-graphrag-integration-tests dependency:build-classpath \
    -DincludeScope=test -Dmdep.outputFile="$runtime_dir/classpath"

classpath="$module_dir/target/test-classes:$module_dir/target/classes:$root_dir/jena-graphrag/target/classes:$(cat "$runtime_dir/classpath")"
if [[ "${GRAPHRAG_SMOKE_DISABLE_GRAPH_RAG:-false}" == 'true' ]]; then
    enable_graphrag=false
    playwright_script='test:disabled-graphrag-smoke'
fi
server_arguments=("$module_dir/src/test/resources/corpus/chat/citation-graph.ttl" "$port" "$dataset" "$enable_graphrag")
if [[ "${GRAPHRAG_SMOKE_REAL_PROVIDERS:-false}" == 'true' ]]; then
    if [[ "$enable_graphrag" != 'true' ]]; then
        echo 'Real-provider smoke requires GraphRAG to be enabled.' >&2
        exit 1
    fi
    provider_environment="$module_dir/env/generated/real-providers.env.sh"
    if [[ ! -r "$provider_environment" ]]; then
        echo 'Real-provider environment is not prepared; run make bootstrap-real-providers first.' >&2
        exit 1
    fi
    # The generated file exports provider values; this script never prints them.
    source "$provider_environment"
    export GRAPHRAG_SYSTEM_PROMPT="${GRAPHRAG_SYSTEM_PROMPT:-Answer from the supplied GraphRAG context and cite it.}"
    runtime_properties=(
        "-Djena.graphrag.drift.communityTopK=$GRAPHRAG_DRIFT_COMMUNITY_TOP_K"
        "-Djena.graphrag.drift.maxFollowUps=$GRAPHRAG_DRIFT_MAX_FOLLOW_UPS"
        "-Djena.graphrag.drift.contextTokenBudget=$GRAPHRAG_DRIFT_CONTEXT_TOKEN_BUDGET"
        "-Djena.graphrag.drift.localTopK=$GRAPHRAG_DRIFT_LOCAL_TOP_K"
    )
    server_arguments+=("$runtime_dir/real-provider-index")
    playwright_script='test:real-providers-smoke'
fi
if [[ "${GRAPHRAG_SMOKE_ULTIMATE:-false}" == 'true' ]]; then
    if [[ "${GRAPHRAG_SMOKE_REAL_PROVIDERS:-false}" != 'true' ]]; then
        echo 'Ultimate PDF smoke requires real providers.' >&2
        exit 1
    fi
    export GRAPHRAG_SYSTEM_PROMPT='Answer only from the ingested GraphRAG research PDF corpus. Cite retrieved sources and say when the corpus lacks the answer.'
    pdf_corpus="$module_dir/src/test/resources/corpus/ingestion/pdf"
    materialized_corpus="$runtime_dir/pdf-corpus.ttl"
    java -cp "$classpath" org.apache.jena.graphrag.integration.PdfCorpusMaterializer "$pdf_corpus" "$materialized_corpus"
    server_arguments[0]="$materialized_corpus"
    playwright_script='test:ultimate-smoke'
fi

java "${runtime_properties[@]}" -cp "$classpath" org.apache.jena.graphrag.fuseki.GraphRAGFusekiUIServer \
    "${server_arguments[@]}" >"$log_file" 2>&1 &
fuseki_pid=$!

for _ in $(seq 1 60); do
    if curl --fail --silent "http://127.0.0.1:$port/\$/ping" >/dev/null; then
        GRAPHRAG_SMOKE_BASE_URL="http://127.0.0.1:$port" npm --prefix "$module_dir" run "$playwright_script"
        exit 0
    fi
    if ! kill -0 "$fuseki_pid" 2>/dev/null; then
        break
    fi
    sleep 1
done

echo 'GraphRAG Fuseki UI did not become healthy; see target/playwright/fuseki.log.' >&2
exit 1
