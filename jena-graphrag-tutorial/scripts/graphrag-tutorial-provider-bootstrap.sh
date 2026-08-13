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

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$MODULE_DIR/.." && pwd)"
ENV_BASE="$MODULE_DIR/env/.env"
ENV_BASE_EXAMPLE="$MODULE_DIR/env/.env.example"
ENV_USER="$MODULE_DIR/env/.env.user"
ENV_EXAMPLE="$MODULE_DIR/env/.env.user.example"
GENERATED_DIR="$MODULE_DIR/env/generated"
GENERATED_SHELL="$GENERATED_DIR/real-providers.env.sh"
GENERATED_MAKE="$GENERATED_DIR/real-providers.mk"
GENERATED_PROPERTIES="$GENERATED_DIR/real-providers.properties"
ENVIRONMENT_VARIABLES=(
    GRAPHRAG_EMBEDDING_API_URL
    GRAPHRAG_EMBEDDING_API_KEY
    GRAPHRAG_EMBEDDING_MODEL
    GRAPHRAG_EMBEDDING_DIMENSION
    OPENAI_API_URL
    OPENAI_API_KEY
    GRAPHRAG_CHAT_MODEL
    GRAPHRAG_DEFAULT_MODE
    GRAPHRAG_DEFAULT_TOP_K
    GRAPHRAG_MAX_TOP_K
    GRAPHRAG_DRIFT_COMMUNITY_TOP_K
    GRAPHRAG_DRIFT_MAX_FOLLOW_UPS
    GRAPHRAG_DRIFT_CONTEXT_TOKEN_BUDGET
    GRAPHRAG_DRIFT_LOCAL_TOP_K
    GRAPHRAG_INDEX_MAX_CONTENT_LENGTH
    GRAPHRAG_TUTORIAL_PDF_PATH
    GRAPHRAG_TUTORIAL_CORPUS_PATH
    GRAPHRAG_TUTORIAL_DATASET
    GRAPHRAG_TUTORIAL_EXPECTED_PDF_COUNT
    GRAPHRAG_INGESTION_BASE_URI
    GRAPHRAG_INGESTION_CHUNK_SIZE
    GRAPHRAG_INGESTION_CHUNK_OVERLAP
    GRAPHRAG_INGESTION_MAX_FILE_SIZE_BYTES
    GRAPHRAG_SERVER_MODE
)

for command_name in git java mvn; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Required command is unavailable: %s\n' "$command_name" >&2
        exit 1
    fi
done

if [[ ! -f "$REPO_ROOT/pom.xml" || ! -f "$MODULE_DIR/pom.xml" ]]; then
    printf 'Jena repository or GraphRAG tutorial module is unavailable\n' >&2
    exit 1
fi

for environment_file in "jena-graphrag-tutorial/env/.env" "jena-graphrag-tutorial/env/.env.user"; do
    if ! git -C "$REPO_ROOT" check-ignore -q "$environment_file"; then
        printf 'Local provider environment file is not ignored by Git\n' >&2
        exit 1
    fi
done

if [[ ! -f "$ENV_BASE" ]]; then
    if [[ -f "$REPO_ROOT/jena-graphrag-integration-tests/env/.env" ]]; then
        cp "$REPO_ROOT/jena-graphrag-integration-tests/env/.env" "$ENV_BASE"
        printf 'Migrated local GraphRAG environment file to: jena-graphrag-tutorial/env/.env\n'
    else
        cp "$ENV_BASE_EXAMPLE" "$ENV_BASE"
        printf 'Created local GraphRAG environment file: jena-graphrag-tutorial/env/.env\n'
    fi
    chmod 0600 "$ENV_BASE"
else
    printf 'Local GraphRAG environment file already exists\n'
fi

if [[ ! -f "$ENV_USER" ]]; then
    if [[ -f "$REPO_ROOT/jena-graphrag-integration-tests/env/.env.user" ]]; then
        cp "$REPO_ROOT/jena-graphrag-integration-tests/env/.env.user" "$ENV_USER"
        printf 'Migrated local provider environment file to: jena-graphrag-tutorial/env/.env.user\n'
    else
        cp "$ENV_EXAMPLE" "$ENV_USER"
        printf 'Created local provider environment file: jena-graphrag-tutorial/env/.env.user\n'
    fi
    chmod 0600 "$ENV_USER"
else
    printf 'Local provider environment file already exists\n'
fi

declare -A provider_values=()
read_environment_file() {
    local environment_file="$1"
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
        if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
            provider_values["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
        else
            printf 'Invalid local provider configuration line\n' >&2
            exit 1
        fi
    done < "$environment_file"
}

read_environment_file "$ENV_BASE"
read_environment_file "$ENV_USER"

if [[ ! -f "$ENV_BASE" || ! -f "$ENV_USER" ]]; then
    printf 'Local provider environment is unavailable\n' >&2
    exit 1
fi

if [[ "${provider_values[GRAPHRAG_TUTORIAL_PDF_PATH]:-}" == "src/test/resources/corpus/ingestion/pdf" ]]; then
    provider_values[GRAPHRAG_TUTORIAL_PDF_PATH]="src/main/resources/corpus/ingestion/pdf"
elif [[ "${provider_values[GRAPHRAG_TUTORIAL_PDF_PATH]:-}" == "src/test/resources/corpus/ingestion/pdf-development" ]]; then
    provider_values[GRAPHRAG_TUTORIAL_PDF_PATH]="src/main/resources/corpus/ingestion/pdf-development"
fi

azure_deployment_name() {
    local endpoint="$1"
    if [[ "$endpoint" =~ /openai/deployments/([^/?]+) ]]; then
        printf '%s' "${BASH_REMATCH[1]}"
    fi
}

documented_embedding_dimension() {
    case "$1" in
        text-embedding-ada-002|text-embedding-3-small|text-embedding-3-large) printf '1024' ;;
    esac
}

openai_compatible_base_url() {
    local endpoint="$1"
    if [[ "$endpoint" =~ ^(https?://[^/]+)/openai/deployments/ ]]; then
        printf '%s/openai/v1/' "${BASH_REMATCH[1]}"
    else
        printf '%s' "$endpoint"
    fi
}

if [[ -z "${provider_values[GRAPHRAG_EMBEDDING_MODEL]:-}" ]]; then
    provider_values[GRAPHRAG_EMBEDDING_MODEL]="$(azure_deployment_name "${provider_values[GRAPHRAG_EMBEDDING_API_URL]:-}")"
fi
if [[ -z "${provider_values[GRAPHRAG_CHAT_MODEL]:-}" ]]; then
    provider_values[GRAPHRAG_CHAT_MODEL]="$(azure_deployment_name "${provider_values[OPENAI_API_URL]:-}")"
fi
if [[ -z "${provider_values[GRAPHRAG_EMBEDDING_DIMENSION]:-}" ]]; then
    provider_values[GRAPHRAG_EMBEDDING_DIMENSION]="$(documented_embedding_dimension "${provider_values[GRAPHRAG_EMBEDDING_MODEL]:-}")"
fi
provider_values[GRAPHRAG_EMBEDDING_API_URL]="$(openai_compatible_base_url "${provider_values[GRAPHRAG_EMBEDDING_API_URL]:-}")"
provider_values[OPENAI_API_URL]="$(openai_compatible_base_url "${provider_values[OPENAI_API_URL]:-}")"

shell_quote() {
    printf "'%s'" "${1//\'/\'\\\'}"
}

make_escape() {
    local value="$1"
    value="${value//\$/\$\$}"
    value="${value//#/\\#}"
    printf '%s' "$value"
}

properties_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//:/\\:}"
    value="${value//=/\\=}"
    value="${value//#/\\#}"
    printf '%s' "$value"
}

property_name() {
    case "$1" in
        GRAPHRAG_EMBEDDING_API_URL) printf 'graphrag.embedding.api.url' ;;
        GRAPHRAG_EMBEDDING_API_KEY) printf 'graphrag.embedding.api.key' ;;
        GRAPHRAG_EMBEDDING_MODEL) printf 'graphrag.embedding.model' ;;
        GRAPHRAG_EMBEDDING_DIMENSION) printf 'graphrag.embedding.dimension' ;;
        OPENAI_API_URL) printf 'openai.api.url' ;;
        OPENAI_API_KEY) printf 'openai.api.key' ;;
        GRAPHRAG_CHAT_MODEL) printf 'graphrag.chat.model' ;;
        GRAPHRAG_DEFAULT_MODE) printf 'jena.graphrag.defaultMode' ;;
        GRAPHRAG_DEFAULT_TOP_K) printf 'jena.graphrag.defaultTopK' ;;
        GRAPHRAG_MAX_TOP_K) printf 'jena.graphrag.maxTopK' ;;
        GRAPHRAG_DRIFT_COMMUNITY_TOP_K) printf 'jena.graphrag.drift.communityTopK' ;;
        GRAPHRAG_DRIFT_MAX_FOLLOW_UPS) printf 'jena.graphrag.drift.maxFollowUps' ;;
        GRAPHRAG_DRIFT_CONTEXT_TOKEN_BUDGET) printf 'jena.graphrag.drift.contextTokenBudget' ;;
        GRAPHRAG_DRIFT_LOCAL_TOP_K) printf 'jena.graphrag.drift.localTopK' ;;
        GRAPHRAG_INDEX_MAX_CONTENT_LENGTH) printf 'jena.graphrag.index.maxContentLength' ;;
        GRAPHRAG_INGESTION_BASE_URI) printf 'jena.graphrag.ingestion.baseUri' ;;
        GRAPHRAG_INGESTION_CHUNK_SIZE) printf 'jena.graphrag.ingestion.chunkSize' ;;
        GRAPHRAG_INGESTION_CHUNK_OVERLAP) printf 'jena.graphrag.ingestion.chunkOverlap' ;;
        GRAPHRAG_INGESTION_MAX_FILE_SIZE_BYTES) printf 'jena.graphrag.ingestion.maxFileSizeBytes' ;;
        GRAPHRAG_SERVER_MODE) printf 'graphrag.server.mode' ;;
    esac
}

mkdir -p "$GENERATED_DIR"
{
    printf '# Generated from env/.env and env/.env.user. Do not edit.\n'
    for variable_name in "${ENVIRONMENT_VARIABLES[@]}"; do
        printf 'export %s=%s\n' "$variable_name" "$(shell_quote "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_SHELL"
{
    printf '# Generated from env/.env and env/.env.user. Do not edit.\n'
    for variable_name in "${ENVIRONMENT_VARIABLES[@]}"; do
        printf 'export %s := %s\n' "$variable_name" "$(make_escape "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_MAKE"
{
    printf '# Generated from env/.env and env/.env.user. Do not edit.\n'
    for variable_name in "${ENVIRONMENT_VARIABLES[@]}"; do
        property="$(property_name "$variable_name")"
        [[ -z "$property" ]] && continue
        printf '%s=%s\n' "$property" "$(properties_escape "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_PROPERTIES"
chmod 0600 "$GENERATED_SHELL" "$GENERATED_MAKE" "$GENERATED_PROPERTIES"

printf 'Generated local environment projections for Make, shell scripts, Maven and Java.\n'
