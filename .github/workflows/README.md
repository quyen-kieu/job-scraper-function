# GitHub Actions — Required Repository Configuration (Stage 8)

**Status as of the initial setup pass:** everything Azure-related is done. Everything
Confluent-related still needs real values from the Confluent Cloud console — see §2b below.

## 1. Environments (Settings → Environments) — ✅ done

- **`dev`** — created, no protection rules (applies automatically for fast iteration).
- **`production`** — created with a `main`-branch-only deployment policy (native GitHub
  enforcement, layered on top of the workflow's own `should_apply` branch check).
  **Repository visibility: PUBLIC.** Required-reviewer protection rules are available for public
  repositories on every plan, so configure a human approval gate for `production`. Note also that
  Actions logs are public - never echo secret or identifying values, and keep such values in
  GitHub *secrets* (auto-masked), not Variables. Configure the reviewer gate with:
  ```powershell
  gh api --method PUT repos/quyen-kieu/job-scraper-function/environments/production `
    -f "reviewers[][type]=User" -f "reviewers[][id]=<your-numeric-github-user-id>"
  ```

## 2a. Secrets/Variables already set (Azure side) — ✅ done

| Name | Kind | Value source |
|---|---|---|
| `AZURE_CLIENT_ID` | Secret | App registration used for GitHub OIDC (appId intentionally not recorded here; see Entra ID > App registrations). Federated credentials exist for `ref:refs/heads/main`, `environment:dev`, `environment:production`, and `pull_request` subjects - no client secret stored, ever. **Important:** the `sub` claim GitHub presents includes numeric owner/repo IDs, e.g. `repo:<owner>@<ownerId>/<repo>@<repoId>:ref:refs/heads/main`, not just `repo:<owner>/<repo>:ref:refs/heads/main` - federated credentials must match this exact format or `azure/login` fails with `AADSTS700213: No matching federated identity record found`. Get the exact values with `gh api repos/<owner>/<repo> --jq "{repoId: .id, ownerId: .owner.id}"`. |
| `AZURE_TENANT_ID` | Secret | This subscription's tenant. |
| `AZURE_SUBSCRIPTION_ID` | Secret | The target Azure subscription ID. |
| `AZURE_CICD_SP_OBJECT_ID` | Variable | The above app's service principal object ID — granted `Contributor` on the `java-functions-group` resource group (for Bicep) and `Storage Blob Data Contributor` on the Terraform state storage account. Also passed into `main.bicep` so it's granted `Key Vault Secrets Officer` on the deployed vault (needed for `deploy-app`'s `az keyvault secret set` calls). |

**Before running `plan-azure` or `apply-azure`, ensure the subscription has the required resource providers registered**:
```powershell
az provider register --namespace Microsoft.DocumentDB --wait
az provider show --namespace Microsoft.DocumentDB --query "registrationState" -o tsv
```
The subscription can deploy Cosmos DB resources only after `Microsoft.DocumentDB` shows `Registered`. This is required for the `cosmos.bicep` module and is not a template bug.

**⚠️ Known gap:** `Contributor` deliberately excludes `Microsoft.Authorization/roleAssignments/write`
(Azure built-in roles never let a principal grant roles unless they're also a role-assignment
administrator). `main.bicep` creates two `Microsoft.Authorization/roleAssignments` resources
(the Function App's Key Vault Secrets User grant, and — when `cicdServicePrincipalObjectId` is
supplied — this same SP's own Key Vault Secrets Officer grant), so `plan-azure`'s `az deployment
group what-if` fails with `AuthorizationFailed` at the vault scope until the SP is *also* granted
a role-assignment-capable role. Fix (run once, by someone with Owner/User Access Administrator on
the subscription — replace `<sp-object-id>` with the `AZURE_CICD_SP_OBJECT_ID` value):
```powershell
az role assignment create `
  --assignee-object-id <sp-object-id> `
  --assignee-principal-type ServicePrincipal `
  --role "Role Based Access Control Administrator" `
  --scope /subscriptions/<subscription-id>/resourceGroups/java-functions-group
```
`Role Based Access Control Administrator` (not `User Access Administrator`) is the narrower
built-in role that grants `roleAssignments/write` while still excluding privilege-escalation-prone
actions like Owner-role assignment — appropriate for a CI/CD identity. Scoping it to the resource
group (rather than the whole vault, which doesn't exist as an ARM scope until Bicep itself creates
it) is sufficient since `roleAssignments/write` is inherited down to child resources.

| `TF_BACKEND_RESOURCE_GROUP` | Variable | `java-functions-group` (created if it didn't already exist). |
| `TF_BACKEND_STORAGE_ACCOUNT` | Variable | A newly created dedicated storage account for Terraform remote state (not the same account Bicep provisions for blob storage). |
| `TF_BACKEND_CONTAINER` | Variable | `confluent`. |
| `KAFKA_CLIENT_API_KEY` / `KAFKA_CLIENT_API_SECRET` | Secrets | Reused directly from the already-working `local.settings.json` (the same cluster-scoped credential proven during Stage 6 manual end-to-end testing). |
| `RESEND_API_KEY` | Secret | Reused directly from `local.settings.json` (the same key that sent the confirmed Stage 6 test email). |
| `KAFKA_BOOTSTRAP_SERVERS` | Secret (per environment) | Confluent bootstrap server (`pkc-xxxxx.<region>.azure.confluent.cloud:9092`). Passed to Bicep at deploy time; deliberately a *secret*, not a Variable or committed parameter, so it is masked in this public repo's Actions logs. |
| `RESEND_FROM_EMAIL` | Secret (per environment) | Verified Resend sender address. Same reasoning as above. |
| `RESEND_TO_EMAIL` | Secret (per environment) | Daily-summary recipient address. Same reasoning as above. Also set repo-level copies (or leave unset) for `infra-validate.yml`, which falls back to placeholders. |
 
## 2b. Still required — Confluent side (not something an agent can obtain on your behalf)

| Name | Kind | Where to get it |
|---|---|---|
| `CONFLUENT_CLOUD_API_KEY` | Secret | Confluent Cloud console → your organization → API keys. Needs **org-admin** scope (topic/ACL/service-account/API-key management) — distinct from the cluster-scoped `KAFKA_CLIENT_API_KEY` already set above, which only has data-plane produce/consume rights. |
| `CONFLUENT_CLOUD_API_SECRET` | Secret | Paired with the key above. |
| `KAFKA_CLIENT_API_KEY` | Secret | Confluent Cloud console → your cluster → API keys. **This is the cluster-scoped credential Terraform must use for `confluent_kafka_topic` and `confluent_kafka_acl` management.** |
| `KAFKA_CLIENT_API_SECRET` | Secret | Paired with the cluster-scoped key above. |
| `CONFLUENT_ENVIRONMENT_ID` | Variable | Confluent Cloud console → Environments (format `env-xxxxxx`). |
| `CONFLUENT_CLUSTER_ID` | Variable | Confluent Cloud console → your cluster's settings (format `lkc-xxxxxx`). The existing `shared-kafka-dev-us-central1` cluster referenced throughout the README. |

Once you have these six values, set them with:
```powershell
gh secret set CONFLUENT_CLOUD_API_KEY --repo quyen-kieu/job-scraper-function
gh secret set CONFLUENT_CLOUD_API_SECRET --repo quyen-kieu/job-scraper-function
gh secret set KAFKA_CLIENT_API_KEY --repo quyen-kieu/job-scraper-function
gh secret set KAFKA_CLIENT_API_SECRET --repo quyen-kieu/job-scraper-function
gh variable set CONFLUENT_ENVIRONMENT_ID --repo quyen-kieu/job-scraper-function --body "env-xxxxxx"
gh variable set CONFLUENT_CLUSTER_ID --repo quyen-kieu/job-scraper-function --body "lkc-xxxxxx"
```

(`gh secret set NAME` with no `--body` prompts interactively so the value never appears in shell
history.)

## 3. Before the very first `apply-confluent` run

Run the one-time `terraform import` runbook in `infra/confluent/README.md` **manually, locally**
first, using the same `CONFLUENT_CLOUD_API_KEY`/`CONFLUENT_CLOUD_API_SECRET` from §2b. The pipeline
does not attempt to auto-detect and self-heal an un-imported state — a clean `terraform plan`
showing 0 unexpected topic changes is a manual pre-flight check the operator confirms once, before
ever letting CI apply.

## 4. Verifying the setup

Open a pull request touching `infra/**` or `src/**` — you should see `validate`, `unit-tests`,
`package`, `plan-azure`, and `plan-confluent` run automatically (read-only, no approval needed).
`plan-azure` should already succeed today (Azure secrets are set); `plan-confluent` will keep
failing at `terraform init`/`plan` until §2b's four Confluent values are set.


## 5. Secret scanning (defense in depth)

- `secret-scan.yml` runs [gitleaks](https://github.com/gitleaks/gitleaks) over the **full git
  history** on every push and pull request (config: `.gitleaks.toml` at the repo root). A finding
  fails the workflow.
- GitHub-native secret scanning + push protection (free for public repos) blocks known credential
  formats at `git push` time. Enable/verify with:
  ```powershell
  gh api --method PATCH repos/<owner>/<repo> `
    -f "security_and_analysis[secret_scanning][status]=enabled" `
    -f "security_and_analysis[secret_scanning_push_protection][status]=enabled"
  ```
- If a scanner ever fires on a real credential: **rotate it first**, then clean history. Removing
  a secret from the latest commit does not un-leak it.