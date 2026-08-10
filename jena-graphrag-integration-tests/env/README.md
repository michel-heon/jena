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

Run the following command from the repository root to create the local provider profile:

```bash
make -C jena-graphrag-integration-tests graphrag-integration-bootstrap-real-providers
```

The bootstrap verifies Git, Java and Maven, creates `.env.user` from `.env.user.example` when it is absent, and verifies that the local file is ignored by Git. It is idempotent and never reads, prints or writes provider values.

Set the seven `GRAPHRAG_*` variables in `.env.user`. The file is local-only and must not be committed. Source it immediately before running a real-provider Maven profile:

```bash
set -a
. jena-graphrag-integration-tests/env/.env.user
set +a
```