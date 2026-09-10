# GitHub Actions — Required Repository Configuration (Stage 8)

The workflows in this directory (`deploy.yml`, `replay.yml`, and Stage 7's `infra-validate.yml`)
reference GitHub Environments, Secrets, and Variables that **must be configured once, manually, in
the repository's Settings** — none of this can be expressed as a committed file. Nothing will run
successfully (beyond `validate`/`unit-tests`/`package`/`plan-*`, which need only the Azure/
Confluent read-level credentials below) until this checklist is complete.

## 1. Environments (Settings → Environments)

Create two environments: **`dev`** and **`production`**.

- **`production`**: add **required reviewers** (at least one person). This is GitHub's native
  approval-gate mechanism — it's what `deploy.yml`'s `environment: production` on the
  `apply-azure`/`apply-confluent`/`deploy-app`/`smoke-test-kafka` jobs actually blocks on
  (README Stage 8 item 6). Optionally also restrict which branches can deploy to this environment
  to `main` (belt-and-suspenders alongside the workflow's own `should_apply` branch check).
- **`dev`**: no required reviewers needed — it's meant to apply automatically for fast iteration.

## 2. Secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret | Used by | Notes |
|---|---|---|
| `AZURE_CLIENT_ID` | all Azure-touching jobs | App registration / user-assigned managed identity configured for OIDC federated credential — no client secret is ever stored. |
| `AZURE_TENANT_ID` | all Azure-touching jobs | |
| `AZURE_SUBSCRIPTION_ID` | all Azure-touching jobs | |
| `CONFLUENT_CLOUD_API_KEY` | `plan-confluent`, `apply-confluent` | Org-admin Confluent Cloud credential, used **only** by the Terraform provider to manage topics/ACLs/service accounts. |
| `CONFLUENT_CLOUD_API_SECRET` | `plan-confluent`, `apply-confluent` | |
| `KAFKA_CLIENT_API_KEY` | `deploy-app` | **Distinct from the pair above** — the cluster-scoped credential the *deployed app itself* authenticates with. Synced into Key Vault's `kafka-username` secret. |
| `KAFKA_CLIENT_API_SECRET` | `deploy-app` | Synced into Key Vault's `kafka-password` secret. |
| `RESEND_API_KEY` | `deploy-app` | Synced into Key Vault's `resend-api-key` secret. |

### Setting up the Azure OIDC federated credential (one-time, via Azure CLI/Portal)

```powershell
# Create (or reuse) an app registration, then add a federated credential scoped to this repo:
az ad app federated-credential create --id <app-object-id> --parameters '{
  "name": "github-actions-deploy",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:<org>/<repo>:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'
```
Add a second federated credential with `"subject": "repo:<org>/<repo>:environment:production"` (and
one for `environment:dev`) so `workflow_dispatch` runs targeting each environment also authenticate.

## 3. Variables (Settings → Secrets and variables → Actions → Variables)

| Variable | Used by | Notes |
|---|---|---|
| `CONFLUENT_ENVIRONMENT_ID` | `plan-confluent`, `apply-confluent` | e.g. `env-xxxxxx`. |
| `CONFLUENT_CLUSTER_ID` | `plan-confluent`, `apply-confluent` | e.g. `lkc-xxxxxx`. |
| `TF_BACKEND_RESOURCE_GROUP` | `plan-confluent`, `apply-confluent` | Resource group hosting the Terraform remote-state storage account (see `infra/confluent/README.md`). |
| `TF_BACKEND_STORAGE_ACCOUNT` | `plan-confluent`, `apply-confluent` | |
| `TF_BACKEND_CONTAINER` | `plan-confluent`, `apply-confluent` | |

## 4. Before the very first `apply-confluent` run

Run the one-time `terraform import` runbook in `infra/confluent/README.md` **manually, locally**
first. The pipeline does not attempt to auto-detect and self-heal an un-imported state — a clean
`terraform plan` showing 0 unexpected topic changes is a manual pre-flight check the operator
confirms once, before ever letting CI apply.

## 5. Verifying the setup

Open a pull request touching `infra/**` or `src/**` — you should see `validate`, `unit-tests`,
`package`, `plan-azure`, and `plan-confluent` run automatically (read-only, no approval needed).
Merging to `main` (or running `workflow_dispatch` with `target_environment: production`) is the
first point at which the `production` environment's required reviewer will be prompted before
`apply-azure` proceeds.

