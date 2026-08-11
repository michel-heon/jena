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

.PHONY: bootstrap-real-providers help real-providers real-providers-smoke smoke

-include env/generated/real-providers.mk

help: ## Show available module targets
	@awk 'BEGIN { FS = ":.*##"; printf "Available targets:\n" } /^[a-zA-Z0-9_-]+:.*##/ { printf "  %-24s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

bootstrap-real-providers: ## Prepare the ignored local real-provider environment file
	@./scripts/graphrag-integration-provider-bootstrap.sh

real-providers: bootstrap-real-providers ## Run the real-provider Maven profile with generated environment
	@mvn -f ../pom.xml -Pgraphrag,graphrag-real-providers -pl jena-graphrag-integration-tests -am test

real-providers-smoke: bootstrap-real-providers ## Run the real-provider Fuseki UI Playwright smoke suite
	@GRAPHRAG_SMOKE_REAL_PROVIDERS=true ./scripts/graphrag-integration-fuseki-smoke.sh

smoke: ## Start the production Fuseki UI and run Playwright smoke tests
	@./scripts/graphrag-integration-fuseki-smoke.sh
