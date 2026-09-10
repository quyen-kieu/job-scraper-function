# Stage 7 Plan — Infrastructure as Code for Azure and Confluent

**Status: PLANNED** (September 9, 2026). This document is the detailed execution plan for
Stage 7 only. Nothing in Stage 8 (GitHub Actions CI/CD *apply* pipeline, secret injection at
deploy time, smoke tests, manual replay workflow) is included here — see README's "Stage 8"
section for that scope, which is explicitly deferred until Stage 7 is reviewed and approved.

Stage 7's job is to make the infrastructure that already exists (manually, today) **reproducible
and reviewable as code**, without changing any application behavior. No Java source changes are
required for Stage 7; every action below is a new file under `infra/` and `.github/workflows/`,
plus README/KAFKA_SETUP.md documentation updates.

---

## 0. Ground truth from the current codebase (constraints the plan must respect)

| Fact | Evidence | Implication for IaC |
|---|---|---|
| Kafka cluster **already exists** (`shared-kafka-dev-us-central1`, Confluent Cloud, Azure/South Central US) and its 5 topics were **created manually** | README "Confluent Kafka topic reference" section | Terraform must **import** existing topics, not create-then-conflict. The environment/cluster itself is a **data source**, not a managed resource — Stage 7 does not provision a new Confluent cluster. |
| Exactly **one** shared Kafka credential pair (`KAFKA_USERNAME` / `KAFKA_PASSWORD`) is read by a single `KafkaProperties` bean and used by every producer and consumer in the app | `KafkaProperties.java` (`username`, `password` fields, no per-listener override) | The README's Stage 7 instruction to "create separate service accounts for scraper, normalizer, metrics, and email consumers" **cannot be wired into the running app without a Java code change** (multiple `ConsumerFactory`/`ProducerFactory` beans, one per credential). Stage 7 will still create the 5 service accounts and least-privilege ACLs in Terraform (good security posture, ready for future adoption), but will explicitly document that the deployed Function App continues to authenticate with the existing single shared credential until a follow-up code change lands. This is a scope decision, not a silent gap — recorded in README same as Stage 5/6 decisions. |
| Cosmos DB auth is **key-based** (`AZURE_COSMOS_KEY`), Blob Storage auth is a **connection string** (`AZURE_STORAGE_CONNECTION_STRING`) | `CosmosProperties.java`, `BlobStorageProperties.java` | No managed-identity code path exists today. Bicep will provision Key Vault secrets for these values and wire them into Function App settings as Key Vault references (`@Microsoft.KeyVault(...)`) — this requires **zero app code changes** because the app only ever sees a resolved string in the env var either way. Switching to `DefaultAzureCredential`/RBAC is called out as a **Stage 7/8+ hardening follow-up**, not part of this plan. |
| No `application.yml`/`.properties` exists; every property is bound purely from environment variables (Function App / `local.settings.json`) | `list_dir` of `src/main/resources`, `grep` for `ConfigurationProperties` | Bicep's Function App module is the single source of truth for **all** app settings; there is no secondary config file to keep in sync. |
| Single Function App hosts all 7 triggers (2 producers + 5 Kafka consumers) | README "Single Function App deployment" | Only **one** Function App resource, one Consumption/Flex plan, one Application Insights instance — matches README's stated deployment boundary. |

---

## 1. Configuration changes

### 1.1 New repository structure

```
infra/
  azure/
    main.bicep
    parameters.dev.json
    parameters.prod.json
    modules/
      function-app.bicep
      storage.bicep
      cosmos.bicep
      keyvault.bicep
      monitoring.bicep
  confluent/
    main.tf
    variables.tf
    outputs.tf
    terraform.tfvars.example
    backend.tf
    modules/
      topics/
        main.tf
        variables.tf
        outputs.tf
      service-accounts/
        main.tf
        variables.tf
        outputs.tf
      acls/
        main.tf
        variables.tf
.github/
  workflows/
    infra-validate.yml     # Stage 7 scope: lint/validate/plan only, no apply
docs/
  STAGE7_PLAN.md            # this file
```

### 1.2 Bicep parameters (per environment: `dev`, `prod`)

| Parameter | Source of truth today | Notes |
|---|---|---|
| `functionAppName` | `pom.xml` `<functionAppName>` (`job-scraper-function-20260815154131779`) | Parameterize; Bicep becomes the canonical name owner going forward, `pom.xml` value kept in sync manually until Stage 8 wires it from a workflow output. |
| `location` | `westus` in `pom.xml` maven plugin config | Keep `westus` for `dev`; allow override per environment. |
| `resourceGroupName` | `java-functions-group` in `pom.xml` | Same. |
| `cosmosDatabaseName`, `cosmosPostingsContainer`, `cosmosObservationsContainer`, `cosmosMetricsContainer` | `local.settings.sample.json` (`AZURE_COSMOS_DATABASE_NAME=job-scraper`, etc.) | Bicep creates the database + 3 containers with these exact names and partition keys (`/companyId` for postings/observations per JobPostingRepository doc comments, `/month` for `monthly-metrics` per README Stage 5 note). |
| `storageContainerName` | `AZURE_STORAGE_CONTAINER_NAME=job-scraper-raw` | Blob container for raw HTML/JSON. |
| `keyVaultName` | new | One Key Vault per environment. |
| `appInsightsName` | new | Enables `FUNCTIONS_EXTENSION_VERSION` diagnostics already present in `pom.xml`'s `appSettings`. |

### 1.3 App settings wired by Bicep (mirrors `local.settings.sample.json` 1:1)

All of the following become Function App application settings, sourced either as plain Bicep
parameters (non-secret) or as Key Vault references (secret):

**Plain (non-secret) settings** — identical values/semantics to `local.settings.sample.json`:
`FUNCTIONS_WORKER_RUNTIME=java`, `FUNCTIONS_EXTENSION_VERSION=~4`, `JOB_SCRAPER_DAILY_CRON`,
`JOB_SCRAPER_USER_AGENT`, `JOB_SCRAPER_REQUEST_TIMEOUT`, `JOB_SCRAPER_MAX_RETRIES`,
`JOB_SCRAPER_ENABLED`, `JOB_SCRAPER_COMPANIES_0_ID`, `JOB_SCRAPER_COMPANIES_0_ENABLED`,
`JOB_SCRAPER_MAX_SEARCH_PAGES_PER_RUN`, `JOB_SCRAPER_MAX_DETAIL_PAGES_PER_RUN`,
`JOB_SCRAPER_REQUEST_DELAY`, `RAW_STORAGE_MODE=blob` (prod; `file` only for local dev),
`KAFKA_ENABLED=true`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SECURITY_PROTOCOL`,
`KAFKA_SASL_MECHANISM`, all 5 `KAFKA_*_TOPIC` names, all 5 `KAFKA_*_CONSUMER_GROUP` names,
`AZURE_STORAGE_ENABLED=true`, `AZURE_STORAGE_CONTAINER_NAME`, `AZURE_COSMOS_ENABLED=true`,
`AZURE_COSMOS_DATABASE_NAME`, `AZURE_COSMOS_POSTINGS_CONTAINER`,
`AZURE_COSMOS_OBSERVATIONS_CONTAINER`, `AZURE_COSMOS_METRICS_CONTAINER`, `RESEND_ENABLED=true`,
`RESEND_FROM_EMAIL`, `RESEND_FROM_NAME`, `RESEND_TO_EMAIL`, `RESEND_SUBJECT_PREFIX`.

**Secret settings** (Key Vault references, e.g.
`@Microsoft.KeyVault(SecretUri=https://<vault>.vault.azure.net/secrets/kafka-password/)`):
`AzureWebJobsStorage` (or switch to identity-based storage if the plan's follow-up is adopted),
`KAFKA_USERNAME`, `KAFKA_PASSWORD`, `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_COSMOS_KEY`,
`RESEND_API_KEY`.

Secrets are **populated into Key Vault out-of-band** (manually today, or by a future Stage 8
workflow step) — Terraform/Bicep create the *slots* (Key Vault + secret resources with
placeholder/rotated-later values), never literal secret values checked into source control.

### 1.4 Terraform variables (Confluent)

| Variable | Value today (manual) | Notes |
|---|---|---|
| `confluent_cloud_api_key` / `confluent_cloud_api_secret` | Confluent Cloud org-level API key | Supplied via `TF_VAR_*` env vars in CI, never in `.tfvars` committed to git. `terraform.tfvars.example` documents the variable names only. |
| `environment_id`, `cluster_id` | `shared-kafka-dev-us-central1`'s IDs (Confluent Cloud console) | Passed in as variables; **cluster is looked up via `data "confluent_kafka_cluster"`, not created.** |
| `topics` | The 5 primary topics + 5 DLTs from the README's "Confluent Kafka topic reference" table | `for_each` map keyed by topic name; partitions=1, `cleanup.policy=delete`, `retention.ms=604800000`, `retention.bytes=-1`, `max.message.bytes=1048576` — exactly the "Topic creation settings (all topics)" table. |
| `service_accounts` | 5 logical roles: scraper, normalizer, persistence, metrics, email | Created in Terraform per README's instruction; **not yet consumed by the app** (see §0 scope decision). |

---

## 2. Implementation plan (file-by-file)

### 2.1 `infra/confluent/` (Terraform)

1. **`main.tf`**
   - `terraform { required_providers { confluent = { source = "confluentinc/confluent" } } }`.
   - `data "confluent_environment"` and `data "confluent_kafka_cluster"` referencing the existing
     `shared-kafka-dev-us-central1` cluster by ID (from `variables.tf`) — **read-only lookup**.
   - `module "topics"` — one instance per primary topic + its DLT (10 total), using the shared
     baseline config table.
   - `module "service_accounts"` — 5 accounts (`sa-job-scraper-scraper`, `sa-job-scraper-normalizer`,
     `sa-job-scraper-persistence`, `sa-job-scraper-metrics`, `sa-job-scraper-email`), each with an
     API key/secret pair (`confluent_api_key` resource), output as **sensitive** Terraform outputs.
   - `module "acls"` — least-privilege grants:
     - scraper SA: `READ` on `job.scrape.commands` (+ consumer group), `WRITE` on
       `job.scrape.completed`.
     - normalizer SA: `READ` on `job.scrape.completed`, `WRITE` on `job.postings.normalized`.
     - persistence SA: `READ` on `job.postings.normalized`.
     - metrics SA: `READ` on `job.postings.normalized`, `WRITE` on `job.metrics.monthly`.
     - email SA: `READ` on `notifications.daily`. A 6th "summary producer" role is implied by the
       topic table (`Summary producer` → `notifications.daily`) — attach `WRITE` on
       `notifications.daily` to the metrics/persistence SA per the aggregator's actual location
       (`RunCompletionAggregator` lives in the Normalizer/Persistence call path), or introduce a
       6th `sa-job-scraper-notifier` SA if a cleaner boundary is preferred — **decision to confirm
       with the team before `terraform apply`**, flagged as an open question below.
   - **Existing-topic import requirement (critical, first-run only):** because all 10 topics
     already exist in the shared cluster, the very first `terraform apply` must be preceded by
     `terraform import` for each one (documented as a numbered runbook in
     `infra/confluent/README.md`, see §3.2), otherwise Terraform will attempt to create a
     duplicate and fail with a 409-style provider error.

2. **`variables.tf`** — declares all inputs above with `sensitive = true` on API key/secret vars.
3. **`outputs.tf`** — exposes topic names (non-sensitive) and per-SA API key/secret (sensitive)
   for later consumption by a Stage 8 secret-sync step (not implemented here).
4. **`backend.tf`** — remote state; recommend an Azure Storage Account + container
   (`tfstateconfluent`) with a `use_azuread_auth` backend block, consistent with README's
   preference to keep Azure as the identity source of truth. Terraform Cloud remains a documented
   alternative in comments.
5. **`terraform.tfvars.example`** — placeholder values only, committed; real `.tfvars` stays
   gitignored.

### 2.2 `infra/azure/` (Bicep)

1. **`main.bicep`** — orchestrates modules, declares all parameters from §1.2, wires module
   outputs to the Function App's `appSettings`.
2. **`modules/storage.bicep`** — Storage Account (Standard_LRS, dev) + blob container
   `job-scraper-raw`; output: connection string (marked `@secure()` at the module boundary, only
   ever flows into the Key Vault module, never as a plain output consumed elsewhere).
3. **`modules/cosmos.bicep`** — Cosmos DB account (**provisioned + `enableFreeTier: true`**, per
   the capacity analysis added after implementation — see `infra/azure/README.md`'s "Cosmos DB:
   free tier capacity analysis" section) + SQL database `job-scraper` + 3 containers with
   partition keys matching the
   existing repository implementations (`job-postings`, `job-observations` on `/companyId`;
   `monthly-metrics` on `/month`, per README Stage 5).
4. **`modules/keyvault.bicep`** — Key Vault with RBAC authorization enabled; grants the Function
   App's system-assigned managed identity the `Key Vault Secrets User` role. Creates secret
   *resources* for the 6 secrets in §1.3 with placeholder values (`changeme-set-via-cli`), so the
   vault and access policy exist and are reviewable, without committing real secrets.
5. **`modules/monitoring.bicep`** — Application Insights + Log Analytics workspace, connected via
   `APPLICATIONINSIGHTS_CONNECTION_STRING` app setting.
6. **`modules/function-app.bicep`** — Flex Consumption plan (Linux, Java 21, matching
   `pom.xml`'s `<runtime><os>linux</os><javaVersion>21</javaVersion>`), system-assigned managed
   identity, all app settings from §1.3 (secrets as Key Vault references using the Key Vault
   module's vault URI output).
7. **`parameters.dev.json` / `parameters.prod.json`** — environment-specific values (resource
   names get an environment suffix, e.g. `job-scraper-kv-dev`).

### 2.3 `.github/workflows/infra-validate.yml` (Stage 7 scope only — validation, not apply)

Mirrors the README's Stage 7 bullet list items 1–3 only (validate/plan/preview), explicitly
**excluding** items 4+ (manual approval, apply, deploy app, smoke test), which belong to Stage 8:

```yaml
name: infra-validate
on:
  pull_request:
    paths: ["infra/**"]
jobs:
  bicep:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: azure/login@v2   # OIDC federated credential, no stored secret
        with: { client-id: ..., tenant-id: ..., subscription-id: ... }
      - run: az bicep build --file infra/azure/main.bicep
      - run: az deployment group what-if --resource-group java-functions-group --template-file infra/azure/main.bicep --parameters infra/azure/parameters.dev.json
  terraform:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: infra/confluent } }
    steps:
      - uses: actions/checkout@v4
      - uses: hashicorp/setup-terraform@v3
      - run: terraform fmt -check -recursive
      - run: terraform init -backend=false
      - run: terraform validate
      - run: terraform plan -input=false
        env:
          TF_VAR_confluent_cloud_api_key: ${{ secrets.CONFLUENT_CLOUD_API_KEY }}
          TF_VAR_confluent_cloud_api_secret: ${{ secrets.CONFLUENT_CLOUD_API_SECRET }}
```

No `apply` step, no `environment:` protection/approval gate, and no deployment of the jar — all of
that is Stage 8.

---

## 3. Documentation changes

### 3.1 `README.md`

- Update the "### Stage 7 – Infrastructure as Code for Azure and Confluent" heading to add
  **`Status: PLANNED (September 9, 2026)`**, matching the Stage 5/6 status-line convention, and a
  one-line pointer: *"See `docs/STAGE7_PLAN.md` for the detailed configuration/implementation/
  documentation plan and open questions."*
- Add a short **"Scope decisions (documented, not deferred silently)"** subsection under Stage 7,
  matching Stage 6's style, capturing the two items already called out in §0 above:
  1. Per-consumer-group Confluent service accounts are provisioned in Terraform but not yet wired
     into the app (single shared `KAFKA_USERNAME`/`KAFKA_PASSWORD` credential remains in use).
  2. Cosmos/Storage secrets flow through Key Vault references but keep key/connection-string auth
     (no managed-identity/RBAC code path yet) — a Stage 7+ hardening follow-up.
  3. Existing Confluent topics require a one-time `terraform import` before the first `apply`.

### 3.2 `infra/confluent/README.md` (new)

A short runbook covering:
- Prerequisites: Confluent Cloud API key with the cluster's admin scope, `TF_VAR_*` env vars.
- **Import runbook** (critical, one-time): the exact `terraform import` command for each of the
  10 existing topics (5 primary + 5 DLT), using their Confluent resource IDs from the console,
  before ever running `terraform apply`.
- How to run `terraform plan` locally against the shared dev cluster safely (read-only by
  default; nothing destructive happens without `apply`).

### 3.3 `infra/azure/README.md` (new)

- How to run `az bicep build` and `az deployment group what-if` locally.
- How Key Vault placeholder secrets get their real values set today (manual `az keyvault secret
  set` step) versus the future Stage 8 automated path.

### 3.4 `KAFKA_SETUP.md`

- Add a note cross-referencing `infra/confluent/` as the now-authoritative, reviewable
  description of topic settings, while keeping the manual setup instructions as the actual
  history of how the cluster/topics were first created (so the import runbook in §3.2 has
  something concrete to import against).

---

## 4. Explicitly out of scope for Stage 7 (belongs to Stage 8 — not started)

- Applying Bicep/Terraform in CI, or any approval-gated `apply` job.
- Deploying the Function App jar / updating live app settings.
- Storing real secret values in GitHub Secrets or writing them into Key Vault from a pipeline.
- The smoke test that publishes a real scrape command end-to-end.
- The manual replay workflow for a selected company/date range.

## 5. Open questions — resolved during implementation

1. ~~Which service account owns `WRITE` ACL on `notifications.daily`~~ — **Resolved:** the
   `persistence` service account, because `NormalizedPostingPersistenceHandler` (not the email
   consumer) is the actual caller of `DailyNotificationPublisher` via `RunCompletionAggregator`.
   No 6th service account was introduced.
2. ~~Terraform remote state backend~~ — **Resolved:** a dedicated Azure Storage Account/container
   (not the same one Bicep provisions for blob storage), configured via `-backend-config` flags
   at `terraform init` time so no environment-specific names are hardcoded in version control.
   See `infra/confluent/README.md`.
3. ~~Adopt managed-identity/RBAC for Cosmos and Storage now?~~ — **Resolved: not yet.** Stage 7
   keeps key/connection-string auth behind Key Vault references (zero app code changes). Flagged
   as a Stage 7+ hardening follow-up in both `infra/azure/README.md` and the README's Stage 7
   scope decisions.
4. **Added after implementation:** Cosmos DB capacity mode — serverless vs. provisioned — was
   revisited once the user clarified a target of **up to 10 companies** polling daily. A per-
   posting RU cost analysis (~35–40 RU/posting) showed realistic burst demand (~330 RU/s at 10
   companies × 50 postings/day) comfortably fits under Azure's free-tier ceiling of 1,000 RU/s.
   **Resolved: switched to provisioned throughput with `enableFreeTier: true`** and a shared
   database-level manual throughput of 1,000 RU/s, making Cosmos DB **$0/month** instead of
   serverless's per-request billing — done before any real deployment/data exists, since capacity
   mode cannot be changed on an existing account. See `infra/azure/README.md`'s "Cosmos DB: free
   tier capacity analysis" section for the full derivation and the one-per-subscription
   constraint.

## 6. Sequencing (once approved)

1. Terraform: add data sources for the existing cluster, run `terraform import` for all 10
   topics, confirm `terraform plan` shows no changes.
2. Terraform: add service accounts + ACLs modules, `plan` (additive only, no topic changes).
3. Bicep: storage, cosmos, keyvault, monitoring modules first (no interdependency on the
   function-app module), validate each with `what-if` individually.
4. Bicep: function-app module wiring all app settings + Key Vault references, validate with
   `what-if` against the existing `java-functions-group` resource group.
5. Add `.github/workflows/infra-validate.yml`, confirm it runs clean on a PR.
6. Update README/KAFKA_SETUP.md/new `infra/*/README.md` docs.
7. Stop — do not begin Stage 8 (per explicit instruction).

