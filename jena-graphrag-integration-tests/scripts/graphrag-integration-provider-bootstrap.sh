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
ENV_USER="$MODULE_DIR/env/.env.user"
ENV_EXAMPLE="$MODULE_DIR/env/.env.user.example"
GENERATED_DIR="$MODULE_DIR/env/generated"
GENERATED_SHELL="$GENERATED_DIR/real-providers.env.sh"
GENERATED_MAKE="$GENERATED_DIR/real-providers.mk"
GENERATED_PROPERTIES="$GENERATED_DIR/real-providers.properties"
PROVIDER_VARIABLES=(
    GRAPHRAG_EMBEDDING_API_URL
    GRAPHRAG_EMBEDDING_API_KEY
    GRAPHRAG_EMBEDDING_MODEL
    GRAPHRAG_EMBEDDING_DIMENSION
    GRAPHRAG_CHAT_API_URL
    GRAPHRAG_CHAT_API_KEY
    GRAPHRAG_CHAT_MODEL
)

for command_name in git java mvn; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        printf 'Required command is unavailable: %s\n' "$command_name" >&2
        exit 1
    fi
done

if [[ ! -f "$REPO_ROOT/pom.xml" || ! -f "$MODULE_DIR/pom.xml" ]]; then
    printf 'Jena repository or GraphRAG integration module is unavailable\n' >&2
    exit 1
fi

if ! git -C "$REPO_ROOT" check-ignore -q "jena-graphrag-integration-tests/env/.env.user"; then
    printf 'Local provider environment file is not ignored by Git: %s\n' "$ENV_USER" >&2
    exit 1
fi

if [[ ! -f "$ENV_USER" ]]; then
    cp "$ENV_EXAMPLE" "$ENV_USER"
    chmod 0600 "$ENV_USER"
    printf 'Created local provider environment file: jena-graphrag-integration-tests/env/.env.user\n'
else
    printf 'Local provider environment file already exists\n'
fi

declare -A provider_values=()
while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        provider_values["${BASH_REMATCH[1]}"]="${BASH_REMATCH[2]}"
    else
        printf 'Invalid local provider configuration line\n' >&2
        exit 1
    fi
done < "$ENV_USER"

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
        GRAPHRAG_CHAT_API_URL) printf 'graphrag.chat.api.url' ;;
        GRAPHRAG_CHAT_API_KEY) printf 'graphrag.chat.api.key' ;;
        GRAPHRAG_CHAT_MODEL) printf 'graphrag.chat.model' ;;
    esac
}

mkdir -p "$GENERATED_DIR"
{
    printf '# Generated from env/.env.user. Do not edit.\n'
    for variable_name in "${PROVIDER_VARIABLES[@]}"; do
        printf 'export %s=%s\n' "$variable_name" "$(shell_quote "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_SHELL"
{
    printf '# Generated from env/.env.user. Do not edit.\n'
    for variable_name in "${PROVIDER_VARIABLES[@]}"; do
        printf 'export %s := %s\n' "$variable_name" "$(make_escape "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_MAKE"
{
    printf '# Generated from env/.env.user. Do not edit.\n'
    for variable_name in "${PROVIDER_VARIABLES[@]}"; do
        printf '%s=%s\n' "$(property_name "$variable_name")" "$(properties_escape "${provider_values[$variable_name]:-}")"
    done
} > "$GENERATED_PROPERTIES"
chmod 0600 "$GENERATED_SHELL" "$GENERATED_MAKE" "$GENERATED_PROPERTIES"

printf 'Generated local environment projections for Make, shell scripts, Maven and Java.\n'