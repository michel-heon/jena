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

.PHONY: bootstrap-real-providers disabled-graphrag-smoke help real-providers real-providers-smoke report smoke

PLAYWRIGHT_REPORT_PORT ?= 9323

-include env/generated/real-providers.mk

help: ## Show available module targets
	@awk 'BEGIN { FS = ":.*##"; printf "Available targets:\n" } /^[a-zA-Z0-9_-]+:.*##/ { printf "  %-24s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

bootstrap-real-providers: ## Prepare the ignored local real-provider environment file
	@./scripts/graphrag-integration-provider-bootstrap.sh

real-providers: bootstrap-real-providers ## Run the real-provider Maven profile with generated environment
	@mvn -f ../pom.xml -Pgraphrag,graphrag-real-providers -pl jena-graphrag-integration-tests -am test

real-providers-smoke: bootstrap-real-providers ## Run the real-provider Fuseki UI Playwright smoke suite
	@GRAPHRAG_SMOKE_REAL_PROVIDERS=true ./scripts/graphrag-integration-fuseki-smoke.sh

disabled-graphrag-smoke: ## Run the Fuseki UI Playwright smoke suite with GraphRAG disabled
	@GRAPHRAG_SMOKE_DISABLE_GRAPH_RAG=true ./scripts/graphrag-integration-fuseki-smoke.sh

report: ## Serve the Playwright HTML report and open it in the Windows browser from WSL
	@test -f target/playwright/report/index.html || { echo 'Playwright report not found; run make smoke first.' >&2; exit 1; }
	@set -eu; \
		url="http://127.0.0.1:$(PLAYWRIGHT_REPORT_PORT)"; \
		log_file=target/playwright/show-report.log; \
		npx playwright show-report --host 0.0.0.0 --port "$(PLAYWRIGHT_REPORT_PORT)" target/playwright/report >"$$log_file" 2>&1 & \
		report_pid=$$!; \
		trap 'kill "$$report_pid" 2>/dev/null || true' EXIT INT TERM; \
		curl --silent --show-error --fail --retry 20 --retry-connrefused --retry-delay 1 "$$url" >/dev/null || { cat "$$log_file" >&2; exit 1; }; \
		powershell.exe -NoProfile -Command "Start-Process '$$url'"; \
		echo "Serving Playwright report at $$url; press Ctrl-C to stop."; \
		wait "$$report_pid"

smoke: ## Start the production Fuseki UI and run Playwright smoke tests
	@./scripts/graphrag-integration-fuseki-smoke.sh
