<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements. See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership. The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied. See the License for the
   specific language governing permissions and limitations
   under the License.

   SPDX-License-Identifier: Apache-2.0
-->

# Local Real-Provider Environment

Run the following command from the repository root to create the local GraphRAG profile:

```bash
make -C jena-graphrag-integration-tests bootstrap-real-providers
```

The bootstrap verifies Git, Java and Maven, creates `.env` from `.env.example` and `.env.user` from `.env.user.example` when either is absent, and verifies that both local files are ignored by Git. `.env` supplies GraphRAG runtime settings; `.env.user` overrides it with local provider settings. It projects the merged values into ignored `env/generated/` files for Make, shell scripts and Maven; Surefire exposes the runtime settings to Java as both environment variables and JVM properties. It is idempotent and never prints provider values.

Set GraphRAG runtime settings and embedding credentials in `.env`; keep chat-specific overrides in `.env.user`. Both files are local-only and must not be committed. `GRAPHRAG_SERVER_MODE` is projected for launch scripts; GraphRAG Java configuration consumes `GRAPHRAG_DEFAULT_MODE`, the top-K limits, the index content limit, and the ingestion settings. Source the generated values immediately before running a real-provider Maven profile:

```bash
set -a
. jena-graphrag-integration-tests/env/generated/real-providers.env.sh
set +a
```