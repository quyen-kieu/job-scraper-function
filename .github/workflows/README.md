# GitHub Actions — Repository Configuration

Verify these settings in the target repository and Azure/Confluent environments before deploying;
do not assume that configuration from an earlier deployment is still present.

## 1. Environments (Settings → Environments)

- **`dev`** — use no protection rules if automatic development deployment is intended.
- **`production`** — configure a `main`-branch-only deployment policy (native GitHub
  enforcement, layered on top of the workflow's own `should_apply` branch check).
  **Repository visibility: PUBLIC.** Required-reviewer protection rules are available for public
  repositories on every plan, so configure a human approval gate for `production`. Note also that
  Actions logs are public - never echo secret or identifying values, and keep such values in
  GitHub *secrets* (auto-masked), not Variables. Configure the reviewer gate with:
  ```powershell
  gh api --method PUT 'repos/{owner}/{repo}/environments/production' `
    -f "reviewers[][type]=User" -f "reviewers[][id]=<your-numeric-github-user-id>"
  ```

## 2a. Azure and application secrets/variables

| Name | Kind | Value source |
|---|---|---|
| `AZURE_CLIENT_ID` | Secret | Client ID of the app registration used for GitHub OIDC. Configure federated credentials matching the actual subjects used by the relevant branch, pull-request, and environment jobs. Immutable OIDC subject formats may include numeric owner and repository IDs; see [GitHub's OIDC reference](https://docs.github.com/en/actions/reference/security/oidc). |
| `AZURE_TENANT_ID` | Secret | This subscription's tenant. |
| `AZURE_SUBSCRIPTION_ID` | Secret | The target Azure subscription ID. |
| `AZURE_CICD_SP_OBJECT_ID` | Variable | The above app's service principal object ID — granted `Contributor` on the target resource group (for Bicep) and `Storage Blob Data Contributor` on the Terraform state storage account. Also passed into `main.bicep` so it's granted `Key Vault Secrets Officer` on the deployed vault (needed for `deploy-app`'s `az keyvault secret set` calls). |

**Before running `plan-azure` or `apply-azure`, ensure the subscription has the required resource providers registered**:
```powershell
az provider register --namespace Microsoft.DocumentDB --wait
az provider show --namespace Microsoft.DocumentDB --query "registrationState" -o tsv
```
The subscription can deploy Cosmos DB resources only after `Microsoft.DocumentDB` shows `Registered`. This is required for the `cosmos.bicep` module and is not a template bug.

**⚠️ Known gap:** `Contributor` deliberately excludes `Microsoft.Authorization/roleAssignments/write`
(Azure built-in roles never let a principal grant roles unless they're also a role-assignment
administrator). `main.bicep` creates `Microsoft.Authorization/roleAssignments` resources
(the Function App's Blob Data Owner and Key Vault Secrets User grants, and — when
`cicdServicePrincipalObjectId` is supplied — the CI service principal's Key Vault Secrets Officer
grant), so `plan-azure`'s `az deployment
group what-if` fails with `AuthorizationFailed` at the vault scope until the SP is *also* granted
a role-assignment-capable role. Fix (run once, by someone with Owner/User Access Administrator on
the subscription — replace `<sp-object-id>` with the `AZURE_CICD_SP_OBJECT_ID` value):
```powershell
az role assignment create `
  --assignee-object-id "<sp-object-id>" `
  --assignee-principal-type ServicePrincipal `
  --role "Role Based Access Control Administrator" `
  --scope "/subscriptions/<subscription-id>/resourceGroups/<resource-group>"
```
Grant role-assignment permissions at the smallest appropriate scope, following your organization's
access policies. The subscription or resource-group permissions required to create the resource
group and register its provider are separate from the permissions on the vault.

| Name | Kind | Value source |
|---|---|---|
| `TF_BACKEND_RESOURCE_GROUP` | Variable | The resource group containing the Terraform state storage account. |
| `TF_BACKEND_STORAGE_ACCOUNT` | Variable | Storage account used for Terraform remote state. |
| `TF_BACKEND_CONTAINER` | Variable | Container used for Terraform remote state. |
| `TFPLAN_ENCRYPTION_KEY` | Secret | Passphrase used to encrypt the Terraform plan artifact in `plan-confluent` and decrypt it in `apply-confluent`. Required for planning even on pull requests. |
| `KAFKA_CLIENT_API_KEY` / `KAFKA_CLIENT_API_SECRET` | Secrets | Cluster-scoped credentials with the required data-plane permissions. |
| `RESEND_API_KEY` | Secret | Resend API key for the target environment. |
| `KAFKA_BOOTSTRAP_SERVERS` | Secret (per environment) | Confluent bootstrap server (`pkc-xxxxx.<region>.azure.confluent.cloud:9092`). Passed to Bicep at deploy time; deliberately a *secret*, not a Variable or committed parameter, so it is masked in this public repo's Actions logs. |
| `RESEND_FROM_EMAIL` | Secret (per environment) | Verified Resend sender address. Same reasoning as above. |
| `RESEND_TO_EMAIL` | Secret (per environment) | Daily-summary recipient address. Same reasoning as above. Also set repo-level copies (or leave unset) for `infra-validate.yml`, which falls back to placeholders. |
 
## 2b. Confluent configuration

| Name | Kind | Where to get it |
|---|---|---|
| `CONFLUENT_CLOUD_API_KEY` | Secret | Confluent Cloud console → your organization → API keys. Needs **org-admin** scope (topic/ACL/service-account/API-key management) — distinct from the cluster-scoped `KAFKA_CLIENT_API_KEY` already set above, which only has data-plane produce/consume rights. |
| `CONFLUENT_CLOUD_API_SECRET` | Secret | Paired with the key above. |
| `KAFKA_CLIENT_API_KEY` | Secret | Confluent Cloud console → your cluster → API keys. **This is the cluster-scoped credential Terraform must use for `confluent_kafka_topic` and `confluent_kafka_acl` management.** |
| `KAFKA_CLIENT_API_SECRET` | Secret | Paired with the cluster-scoped key above. |
| `CONFLUENT_ENVIRONMENT_ID` | Secret | Confluent Cloud console → Environments (format `env-xxxxxx`). The workflows read this from `secrets`, not `vars`. |
| `CONFLUENT_CLUSTER_ID` | Secret | Confluent Cloud console → your target cluster's settings (format `lkc-xxxxxx`). The workflows read this from `secrets`, not `vars`. |

Set these six Confluent values and the plan-encryption passphrase with:
```powershell
gh secret set CONFLUENT_CLOUD_API_KEY
gh secret set CONFLUENT_CLOUD_API_SECRET
gh secret set KAFKA_CLIENT_API_KEY
gh secret set KAFKA_CLIENT_API_SECRET
gh secret set CONFLUENT_ENVIRONMENT_ID
gh secret set CONFLUENT_CLUSTER_ID
gh secret set TFPLAN_ENCRYPTION_KEY
```

(`gh secret set NAME` with no `--body` prompts interactively so the value never appears in shell
history.) Store secrets at repository level or in the target GitHub Environment as appropriate;
environment secrets with the same name take precedence in environment-bound jobs.

## 3. Before the very first `apply-confluent` run

Follow the [Confluent import runbook](../../infra/confluent/README.md) for existing resources.
The deployment workflow attempts imports before planning, but review the resulting plan and any
import errors before approving an apply. Manual imports may be needed when managing state outside
the workflow.

## 4. Verifying the setup

Open a pull request touching `infra/**` or `src/**` — you should see `validate`, `unit-tests`,
`package`, `plan-azure`, and `plan-confluent` run automatically. Both plans require valid
credentials, permissions, backend configuration, and any approval required by the configured
GitHub environment.

**Planning is not entirely read-only:** `plan-azure` registers a resource provider and runs
`az group create` before its what-if operation. `plan-confluent` attempts backend resource
creation and imports existing resources into the remote Terraform state before saving a plan.
Its lock-retry helper may force-unlock state; coordinate other writers as described in the
[Confluent runbook](../../infra/confluent/README.md).
The saved Terraform plan can contain sensitive values. The workflow encrypts it with
`TFPLAN_ENCRYPTION_KEY` before uploading the artifact and decrypts it for apply; protect the
passphrase and avoid sharing plan contents.

On a qualifying push to `main`, the workflow targets `production`; manual dispatch can target
`dev` or `production`, with production apply restricted to `main`. The Azure CLI steps use
the resource group configured directly in the workflow, not the Maven resource-group default.
`replay.yml` manually triggers a **live** company scrape, not a historical replay; its date
inputs are currently unused. The separate `infra-validate.yml` runs on infrastructure pull
requests; its optional Terraform plan is gated by `TF_PLAN_ENABLED`. That plan step does not
supply the required cluster Kafka key and secret and initializes without the remote backend, so
enabling the flag alone does not provide a complete production-state plan.


## 5. Secret scanning (defense in depth)

- `secret-scan.yml` runs [gitleaks](https://github.com/gitleaks/gitleaks) over the **full git
  history** for credentials (`.gitleaks.toml`) and the current checkout for email addresses
  (`.gitleaks-pii.toml`). It runs on pushes, pull requests, manual dispatch, and a weekly schedule;
  findings fail the workflow. Scanning is defense in depth, not proof that no private data exists.
- GitHub-native secret scanning + push protection (free for public repos) blocks known credential
  formats at `git push` time. Enable/verify with:
  ```powershell
  gh api --method PATCH 'repos/{owner}/{repo}' `
    -f "security_and_analysis[secret_scanning][status]=enabled" `
    -f "security_and_analysis[secret_scanning_push_protection][status]=enabled"
  ```
- If a scanner ever fires on a real credential: **rotate it first**, then clean history. Removing
  a secret from the latest commit does not un-leak it.