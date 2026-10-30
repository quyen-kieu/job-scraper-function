# Confluent Terraform — Runbook

Manages the 5 primary topics + 5 dead-letter topics, per-role service accounts, and least-privilege
ACLs against the **existing** shared cluster `shared-kafka-dev-us-central1` (Azure / South Central
US). This module never creates a cluster or environment — both are read as data sources.

## Prerequisites

- Terraform >= 1.7.0.
- A Confluent Cloud **organization-level API key** with permission to manage Kafka topics, ACLs,
  service accounts, and API keys within the target environment/cluster.
- The cluster's `environment_id` (`env-xxxxxx`) and `cluster_id` (`lkc-xxxxxx`) from the Confluent
  Cloud console.

Export credentials as environment variables — never write them into a committed `.tfvars` file:

```powershell
$env:TF_VAR_confluent_cloud_api_key = "<key>"
$env:TF_VAR_confluent_cloud_api_secret = "<secret>"
```

Copy `terraform.tfvars.example` to `terraform.tfvars` (gitignored) for the non-secret variables
(`confluent_environment_id`, `confluent_cluster_id`), or pass them with `-var`.

## Remote state

Uses Azure AD auth (`use_azuread_auth=true`), not a storage account access key — the CI/CD
identity is granted `Storage Blob Data Contributor` on the state storage account (RBAC), never an
account key, matching this project's preference for identity-based auth over shared secrets.

```powershell
terraform init `
  -backend-config="resource_group_name=<rg>" `
  -backend-config="storage_account_name=<tfstate-storage-account>" `
  -backend-config="container_name=confluent" `
  -backend-config="key=<env>.tfstate" `
  -backend-config="use_azuread_auth=true"
```

In CI (`.github/workflows/deploy.yml`), these same four values come from the
`TF_BACKEND_RESOURCE_GROUP` / `TF_BACKEND_STORAGE_ACCOUNT` / `TF_BACKEND_CONTAINER` GitHub
Variables and a `<bicep_env>.tfstate` key — see `.github/workflows/README.md`.

## ⚠️ Existing-resource import (before the first `apply`)

All 10 topics already exist — they were created manually against the shared cluster before this
Terraform module existed (see the root README's "Confluent Kafka topic reference" section). If you
run `terraform apply` against a fresh state without importing first, the provider will fail with a
"topic already exists" error.

1. Run `terraform init` (see above) and `terraform plan` once — expect it to show 10 topics, 5
   service accounts, and ~17 ACLs as **to be created**. Do **not** apply yet.
2. For each of the 10 topic names below, run:

   ```powershell
   terraform import 'module.topics.confluent_kafka_topic.this["<topic-name>"]' "<cluster-id>/<topic-name>"
   ```

   Topic names to import:
   ```
   job.scrape.commands
   job.scrape.commands.DLT
   job.scrape.completed
   job.scrape.completed.DLT
   job.postings.normalized
   job.postings.normalized.DLT
   job.metrics.monthly
   job.metrics.monthly.DLT
   notifications.daily
   notifications.daily.DLT
   ```

3. Re-run `terraform plan`. It should now show 0 changes for topics (config already matches the
   README's shared baseline), and only the *new* resources (service accounts, API keys, ACLs) as
   "to be created" — those are genuinely new and do not need importing.
4. Only after step 3 shows a clean, expected plan should `terraform apply` be run — and only as
   part of the approval-gated Stage 8 pipeline (`.github/workflows/deploy.yml`'s `apply-confluent`
   job), not ad hoc. The pipeline automatically imports existing topics, service accounts, and
   API keys in `plan-confluent` before it saves the plan. These manual steps are only needed when
   managing Terraform state outside that pipeline; do not change state between the pipeline's plan
   and apply jobs.

### ⚠️ Gotcha: every `terraform import` step in CI needs the full `TF_VAR_*` set

`terraform import` evaluates the **entire** configuration graph (provider block, data sources,
locals) before it can import a single resource instance — not just the one resource being
imported. If any `TF_VAR_confluent_cloud_api_key` / `TF_VAR_confluent_cloud_api_secret` /
`TF_VAR_kafka_api_key` / `TF_VAR_kafka_api_secret` / `TF_VAR_confluent_environment_id` /
`TF_VAR_confluent_cluster_id` value is missing from a given import step's `env:` block, the
`terraform import` command fails immediately with `Error: No value for required variable`.

This is easy to miss because the import loops in `deploy.yml` wrap each `terraform import` call so
a failure doesn't abort the step (a topic/service-account/API-key that genuinely doesn't exist yet
is expected to "fail" to import and fall through to being created by `terraform plan`/`apply`
instead). If you only print/log the failure instead of hard-failing the step, a **missing
`TF_VAR_*`** looks identical to "doesn't exist yet" — nothing gets imported, `terraform plan`
re-plans a `create` for a resource that already exists in Confluent, and `apply-confluent` later
fails with a `409 Conflict: ... already in use` or `400 Bad Request: ... already exists`.

When adding or editing an import step in `deploy.yml`:
- Set **all six** `TF_VAR_*` values in that step's `env:` block (copy them from the `terraform
  plan` step below it — don't assume they're inherited from another step).
- Never discard the `terraform import` command's stderr. Capture it and `echo` it (or emit a
  `::warning::`/`::error::` annotation) so a genuine bug is visible in the Actions log instead of
  silently producing a plan full of unwanted `create`s.

## Scope decision reminder

The 5 service accounts and their ACLs are **not yet consumed by the deployed Function App** — the
app currently authenticates with one shared `KAFKA_USERNAME`/`KAFKA_PASSWORD` credential
(`KafkaProperties` has no per-listener override). See `docs/STAGE7_PLAN.md` for the follow-up
required to adopt per-role credentials in code.
