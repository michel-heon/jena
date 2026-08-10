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

printf 'Set the required GRAPHRAG_* values locally, then source env/.env.user before running the real-provider profile.\n'