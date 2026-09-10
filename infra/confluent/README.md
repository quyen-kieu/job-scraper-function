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

## ⚠️ Required one-time import (before the first `apply`)

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
   job), not ad hoc. That pipeline does **not** perform this import automatically — it assumes
   steps 1–3 above have already been completed manually, exactly once, per environment.

## Scope decision reminder

The 5 service accounts and their ACLs are **not yet consumed by the deployed Function App** — the
app currently authenticates with one shared `KAFKA_USERNAME`/`KAFKA_PASSWORD` credential
(`KafkaProperties` has no per-listener override). See `docs/STAGE7_PLAN.md` for the follow-up
required to adopt per-role credentials in code.

