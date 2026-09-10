# Stage 8 Plan — GitHub Actions CI/CD for Application and Infrastructure Deployment

**Status: IMPLEMENTED** (September 9, 2026). This document is both the plan and the as-built
record for Stage 8 — the README's own convention (see Stage 5–7) is to document scope decisions
rather than defer them silently, so deviations from the README's literal 13-item list are called
out explicitly below with the reasoning.

Nothing in this stage has been **run** against real Azure/Confluent/GitHub infrastructure yet —
the workflows are implemented and validated for syntax, but they require real GitHub repository
configuration (Environments, Secrets, Variables — see §3) before the `apply-*`/`deploy-app`/
`smoke-test-kafka` jobs can execute for real. That one-time setup is a manual, out-of-band step
documented in `.github/workflows/README.md`, consistent with how Stage 7's Confluent topic import
runbook was handled.

---

## 0. Ground truth from Stage 7 that this plan builds on

| Fact | Source | Implication |
|---|---|---|
| `infra/azure/main.bicep` already provisions the Function App, Storage, Cosmos, Key Vault, monitoring | Stage 7 | `apply-azure` = `az deployment group create` against this template; nothing new to write. |
| `infra/confluent/main.tf` already manages topics/service-accounts/ACLs against the **existing** cluster | Stage 7 | `apply-confluent` = `terraform apply`; the one-time `terraform import` runbook (`infra/confluent/README.md`) must run once, manually, before the first CI-driven apply. |
| Key Vault secrets `kafka-username`, `kafka-password`, `resend-api-key` are created with **empty placeholders** by Bicep | Stage 7 | Stage 8's `deploy-app` job is the natural place to populate them for real from GitHub Secrets, closing that Stage 7 forward-reference ("populated out-of-band... or a future Stage 8 automated step"). |
| `pom.xml`'s `azure-functions-maven-plugin` hardcodes `<appName>${functionAppName}</appName>` = `job-scraper-function-20260815154131779`, but Bicep names the Function App `job-scraper-function-dev` / `job-scraper-function-prod` | `pom.xml` vs. `infra/azure/main.bicep`'s `functionAppName` variable | These two names have diverged since Stage 7. Stage 8 does **not** edit `pom.xml` (avoids hardcoding an environment into source); instead the CD workflow overrides `-DfunctionAppName=job-scraper-function-<env>` on the Maven command line, since `functionAppName` is a plain `<properties>` entry and Maven properties are overridable via `-D`. |
| `azure-functions-maven-plugin`'s `deploy` goal is provision-**and**-deploy (upsert), not deploy-only | Plugin behavior | Running it against a Bicep-provisioned app is safe (idempotent upsert; only merges the one `FUNCTIONS_EXTENSION_VERSION` app setting declared in `pom.xml`, does not remove Bicep's other settings) but is a real risk if the plugin's own resource-shape assumptions ever drift from Bicep's. Documented as a scope decision (§4) rather than writing a parallel `az functionapp deploy` zip-based path from scratch. |
| Every Kafka topic has 1 partition; `JOB_SCRAPER_REQUEST_DELAY` throttles scraping | Stage 7 cost analysis | The smoke test in §2.6 can safely re-trigger a real on-demand scrape without fear of overwhelming Cosmos DB's free-tier throughput. |
| No `replay`/rebuild HTTP trigger exists; `MonthlyMetricRebuildService` (Stage 5) is an internal-only service with **no Function trigger**, by deliberate design ("preserving the fixed function topology") | Stage 5 README note | The README's Stage 8 item 13 ("manual replay workflow for a selected company and date range") cannot be wired to true historical reprocessing without adding a new Function trigger, which would contradict that Stage 5 decision. Resolved as a scope decision in §4: the replay workflow re-triggers a **live** on-demand scrape (already exposed via `OnDemandScrapeCommandProducer`), and the historical-rebuild gap is documented, not silently papered over. |

---

## 1. Configuration changes

### 1.1 New files

```
.github/
  workflows/
    deploy.yml      # the full Stage 8 pipeline (validate -> ... -> smoke-test-kafka)
    replay.yml       # manual workflow_dispatch replay (README item 13)
    README.md         # required repo configuration runbook (Environments, Secrets, Variables)
scripts/
  validate_kafka_config.py   # README item 2: cross-checks topic/consumer-group name drift
  smoke_test.sh               # README item 12: triggers + verifies a real event path
docs/
  STAGE8_PLAN.md              # this file
```

### 1.2 Required GitHub repository configuration (one-time, manual, not code)

**Environments** (Settings → Environments): `dev` and `production`. `production` must have
**required reviewers** configured — this is GitHub's native approval-gate mechanism (README item
6) and cannot be expressed in a workflow YAML file; it's a repository setting `deploy.yml`
references via `environment: production` on the gated jobs.

**Secrets** (Settings → Secrets and variables → Actions):

| Secret | Purpose |
|---|---|
| `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | OIDC federated credential for `azure/login` (no stored client secret) — already used by Stage 7's `infra-validate.yml`. |
| `CONFLUENT_CLOUD_API_KEY`, `CONFLUENT_CLOUD_API_SECRET` | Org-admin Confluent Cloud credential, used only by the Terraform provider to manage topics/ACLs/service accounts. |
| `KAFKA_CLIENT_API_KEY`, `KAFKA_CLIENT_API_SECRET` | **Distinct from the pair above** — the cluster-scoped credential the *deployed app itself* authenticates with (`KAFKA_USERNAME`/`KAFKA_PASSWORD`). Synced into Key Vault's `kafka-username`/`kafka-password` secrets by `deploy-app`. |
| `RESEND_API_KEY` | Synced into Key Vault's `resend-api-key` secret by `deploy-app`. |

**Variables** (non-secret, Settings → Secrets and variables → Actions → Variables):

| Variable | Purpose |
|---|---|
| `CONFLUENT_ENVIRONMENT_ID`, `CONFLUENT_CLUSTER_ID` | Passed to Terraform as `TF_VAR_confluent_environment_id` / `TF_VAR_confluent_cluster_id`. |
| `TF_BACKEND_RESOURCE_GROUP`, `TF_BACKEND_STORAGE_ACCOUNT`, `TF_BACKEND_CONTAINER` | Terraform's `azurerm` remote state backend, passed to `terraform init -backend-config=...` (per `infra/confluent/README.md`). |

### 1.3 Environment-to-resource-name mapping

| GitHub environment | Bicep `environmentName` | Function App name | Resource group |
|---|---|---|---|
| `dev` | `dev` | `job-scraper-function-dev` | `java-functions-group` |
| `production` | `prod` | `job-scraper-function-prod` | `java-functions-group` |

---

## 2. Implementation (file-by-file)

### 2.1 `.github/workflows/deploy.yml` — the full Stage 8 pipeline

One workflow file implements the entire README pipeline diagram
(`validate -> unit-tests -> package -> plan-azure -> plan-confluent -> approval-gate -> apply-azure
-> apply-confluent -> deploy-app -> smoke-test-kafka`) as a single `jobs:` graph with `needs:`
edges matching that order exactly. Triggers:

- `pull_request` (any branch) — runs `validate`, `unit-tests`, `package`, `plan-azure`,
  `plan-confluent` only. The `apply-*`/`deploy-app`/`smoke-test-kafka` jobs are gated with
  `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`, so PRs only ever see
  read-only validation/preview — this is how README item 11 ("restrict production deployment to
  the main branch") is enforced, structurally, not just by convention.
- `push` to `main` — runs the full pipeline, including apply/deploy/smoke-test.
- `workflow_dispatch` with a `target_environment` choice input (`dev`/`production`) — for a manual
  re-run against `dev` without needing a push to `main`.

Jobs:

1. **`validate`** — `az bicep build`, `terraform fmt -check` + `terraform validate` (mirrors
   `infra-validate.yml`'s checks so this pipeline is self-contained), plus
   `scripts/validate_kafka_config.py` (README item 2).
2. **`unit-tests`** — `mvn test` (integration-tagged tests excluded by the existing surefire
   config, unchanged).
3. **`package`** — `mvn -DskipTests clean package`, uploads the `target/azure-functions/**`
   staging directory as a build artifact for the `deploy-app` job to reuse (avoids rebuilding
   twice, and guarantees `deploy-app` deploys exactly what `unit-tests` validated).
4. **`plan-azure`** — `az deployment group what-if` against `parameters.<env>.json` (`dev` for
   PRs/dispatch-dev, `prod` for push-to-main/dispatch-prod).
5. **`plan-confluent`** — `terraform plan` (real remote backend this time, unlike
   `infra-validate.yml`'s stateless `-backend=false` preview).
6. **`apply-azure`** (`needs: [plan-azure, plan-confluent]`, `environment: <env>`) —
   `az deployment group create`. Gated behind the GitHub Environment's required reviewers for
   `production` (README item 6); `dev` has no required reviewers configured, so it applies
   automatically.
7. **`apply-confluent`** (`needs: [plan-azure, plan-confluent]`, `environment: <env>`) —
   `terraform apply -auto-approve` (approval already happened at the environment gate).
8. **`deploy-app`** (`needs: [apply-azure, apply-confluent, package]`) —
   - Downloads the `package` job's artifact.
   - `az keyvault secret set` for `kafka-username`/`kafka-password`/`resend-api-key` from the
     GitHub Secrets in §1.2 (closes the Stage 7 forward-reference).
   - `mvn azure-functions:deploy -DfunctionAppName=job-scraper-function-<env>` (see §4 for why the
     combined plugin, not a hand-rolled zip-deploy script, is used here).
9. **`smoke-test-kafka`** (`needs: deploy-app`) — runs `scripts/smoke_test.sh` (README item 12).

### 2.2 `.github/workflows/replay.yml` — manual replay (README item 13)

`workflow_dispatch` inputs: `environment` (`dev`/`production` choice), `company_id` (string,
required), `from_date`/`to_date` (string, optional, documented-but-currently-unused — see §4 scope
decision). Calls the deployed `OnDemandScrapeCommandProducer` HTTP endpoint for `company_id`,
gated behind the same `environment:` protection as `deploy.yml`'s production jobs.

### 2.3 `scripts/validate_kafka_config.py`

Parses `local.settings.sample.json`'s `KAFKA_*_TOPIC`/`KAFKA_*_CONSUMER_GROUP` values and
`infra/confluent/main.tf`'s `local.primary_topics`/`local.consumer_groups` blocks, fails (non-zero
exit) if the two sets ever drift apart. Pure standard-library Python (no extra CI dependency).

### 2.4 `scripts/smoke_test.sh`

1. Fetches a Function-level key via `az functionapp function keys list` for
   `OnDemandScrapeCommandProducer`.
2. `curl -f` a POST to the deployed endpoint for a designated smoke-test company
   (`SMOKE_TEST_COMPANY_ID`, defaults to `mckesson`, the only company configured today).
3. Polls Application Insights via `az monitor app-insights query` (KQL: `traces | where message
   contains "Scrape completed for company"`) for up to a configurable timeout (default 5 minutes),
   retrying every 15 seconds.
4. Exits non-zero if the expected trace never appears — fails the pipeline, matching README item
   12's "verifies the expected event path."

### 2.5 `.github/workflows/README.md`

The required one-time GitHub repository configuration runbook from §1.2 above (Environments,
Secrets, Variables) — nothing here is expressible as a committed file, so it's documented as a
manual checklist instead.

---

## 3. Documentation changes

- `README.md`'s Stage 8 section: status line updated to `IMPLEMENTED`, each of the 13 items
  annotated with ✅ and a pointer to the implementing file, plus a scope-decisions subsection
  (matching Stages 5–7's style) covering the `pom.xml` name mismatch, the Maven-plugin-deploy
  trade-off, and the replay-workflow gap.
- `.github/workflows/README.md` (new, per §2.5).
- `infra/azure/README.md`: cross-reference `deploy.yml` as the now-canonical way to apply/deploy
  (superseding the "manual `az deployment group create`" instructions for day-to-day use, which
  remain documented for local/one-off use).
- `infra/confluent/README.md`: cross-reference `deploy.yml`'s `apply-confluent` job, and reiterate
  that the one-time `terraform import` runbook must still run manually first, before the very
  first CI-driven `terraform apply`.

---

## 4. Scope decisions (documented, not deferred silently)

1. **`pom.xml` is not edited to hardcode an environment-specific Function App name.** The CD
   workflow overrides `-DfunctionAppName=job-scraper-function-<env>` per environment instead,
   since `functionAppName` is a plain Maven property. `pom.xml`'s literal default
   (`job-scraper-function-20260815154131779`) remains only as a local-dev fallback for anyone
   running `mvn azure-functions:deploy` by hand without the override.
2. **`deploy-app` reuses the existing `azure-functions-maven-plugin` `deploy` goal** (a
   provision-and-deploy upsert) rather than writing a new `az functionapp deploy` zip-based path.
   This is safe against a Bicep-provisioned app today (idempotent; only merges the one
   `FUNCTIONS_EXTENSION_VERSION` setting declared in `pom.xml`, doesn't remove Bicep's other
   settings) but is a real coupling to monitor if the plugin's assumptions ever drift from Bicep's
   resource shape — flagged, not silently accepted forever.
3. **The replay workflow (README item 13) re-triggers a live on-demand scrape, not a historical
   reprocessing.** `MonthlyMetricRebuildService` (Stage 5) exists specifically for rebuilding a
   month from `job-observations` after a normalization rule change, but has **no Function
   trigger** by deliberate design (preserving the fixed function topology documented in Stage 5).
   Wiring a true "date range" replay would require adding a new trigger, contradicting that
   decision. `replay.yml` accepts `from_date`/`to_date` inputs for forward compatibility and
   documents them as currently unused, rather than silently dropping the requirement or secretly
   adding an 8th Function without discussion.
4. **The smoke test hits the real, live company careers site** (via a genuine on-demand scrape of
   `mckesson`), not a mocked/synthetic event. This mirrors the exact manual verification already
   performed by hand during Stage 6 (see the chat transcript where a real Resend email was
   confirmed received) — now automated. This is acceptable at this project's scale (single company,
   `JOB_SCRAPER_REQUEST_DELAY` throttling, single-partition topics) but would need reconsideration
   if company count/frequency grows enough to make repeated CI-triggered scrapes a scraping-
   etiquette or rate-limit concern.
5. **Terraform's `apply-confluent` job assumes the one-time `terraform import` runbook
   (`infra/confluent/README.md`) has already been run manually.** The pipeline does not attempt to
   auto-detect and self-heal an un-imported state — that would risk silently creating duplicate
   topics if the import step were skipped. A clean `terraform plan` showing 0 unexpected topic
   changes is a manual pre-flight the operator confirms once, per that runbook.

## 5. Explicitly out of scope (no Stage 9 exists yet — flag for a human decision if one is needed)

- Multi-region / disaster-recovery deployment.
- Blue/green or canary deployment strategies for the Function App.
- Automated rollback on smoke-test failure (today: pipeline just fails; rollback is manual).
- A true historical-replay Function trigger for `MonthlyMetricRebuildService` (see scope decision 3).

