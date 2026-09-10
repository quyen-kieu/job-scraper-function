# Azure Bicep — Runbook

Provisions the single-Function-App deployment boundary described in the root README's
"Single Function App deployment" section: one Flex Consumption plan, one Storage Account, one
Cosmos DB account (provisioned throughput, free tier, 3 containers), one Key Vault, and
Application Insights/Log Analytics for monitoring.

## Prerequisites

- Azure CLI >= 2.60 with the `bicep` extension (`az bicep install`).
- Contributor access to the target resource group (`java-functions-group` for `dev`, per
  `pom.xml`'s existing maven plugin configuration).

## Validate (no changes made)

```powershell
az bicep build --file infra/azure/main.bicep
az deployment group what-if `
  --resource-group java-functions-group `
  --template-file infra/azure/main.bicep `
  --parameters infra/azure/parameters.dev.json
```

## Deploy (manual, until Stage 8 automates this behind an approval gate)

**Stage 8 now automates this** via `.github/workflows/deploy.yml`'s `apply-azure` job (gated
behind the `production` GitHub Environment's required reviewer). The manual commands below remain
useful for local/one-off testing against `dev`, or for diagnosing a failed pipeline run.

```powershell
az deployment group create `
  --resource-group java-functions-group `
  --template-file infra/azure/main.bicep `
  --parameters infra/azure/parameters.dev.json
```

## Setting real secret values

`main.bicep` creates Key Vault secret **resources** with empty placeholder values (empty string,
per Bicep linter guidance for `@secure()` parameter defaults) for `kafka-username`,
`kafka-password`, and `resend-api-key` — the `azure-storage-connection-string` and
`azure-cosmos-key` secrets are populated automatically from the Storage/Cosmos modules' own
outputs. Set the placeholder-backed secrets after the first deployment:

```powershell
az keyvault secret set --vault-name <keyVaultName-output> --name kafka-username --value "<api-key>"
az keyvault secret set --vault-name <keyVaultName-output> --name kafka-password --value "<api-secret>"
az keyvault secret set --vault-name <keyVaultName-output> --name resend-api-key --value "<resend-api-key>"
```

The Function App resolves these as `@Microsoft.KeyVault(SecretUri=...)` app settings at runtime
via its system-assigned managed identity (granted `Key Vault Secrets User` by `main.bicep`). No
Java code change is required — `KafkaProperties`, `CosmosProperties`, `BlobStorageProperties`, and
`ResendProperties` only ever see the resolved string value, identical to a plain app setting.

**Known limitation:** Key Vault reference resolution requires the RBAC role assignment to have
propagated; on a brand-new deployment, a restart of the Function App may be needed if it starts
before the role assignment finishes propagating (typically under a minute).

## Circular-dependency note (why the Key Vault role assignment lives in `main.bicep`, not `keyvault.bicep`)

The Function App's app settings need the Key Vault's URI (to build `SecretUri=...` references),
and the Key Vault RBAC grant needs the Function App's managed identity principal id. Making either
module depend on the other's output directly creates a circular module dependency that Bicep
rejects. The fix: `keyvault.bicep` never takes the Function App's principal id as a parameter;
`main.bicep` creates the `Microsoft.Authorization/roleAssignments` resource itself, after both the
`keyVault` and `functionApp` modules have been deployed.

## Scope decisions (Stage 7)

- Cosmos and Blob Storage keep key/connection-string auth (Key Vault only changes *where* the
  secret is stored, not *how* the app authenticates). Adopting managed-identity/RBAC-based Cosmos
  and Storage access is a Stage 7+ hardening follow-up requiring a Java code change.
- `pom.xml`'s `azure-functions-maven-plugin` config still hardcodes `resourceGroup`/
  `appServicePlanName`/`region`, which match these Bicep defaults today but aren't read from the
  same source — keep them in sync manually if either changes. `appName` is overridden per
  environment at deploy time by `.github/workflows/deploy.yml` (see `docs/STAGE8_PLAN.md`), so
  only `appName` itself is exempt from this manual-sync concern.

## Estimated Azure costs

**None of the resources provisioned here are unconditionally free** — but at this project's
projected scale (up to 10 companies polling once per day), most land at or near $0/month because
of Azure's free grants. Prices below are approximate, US-region, pay-as-you-go, and change over
time — treat this as a starting point, not a quote; run the
[Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/) for anything precise,
and set up a Cost Management budget/alert once deployed for real.

| Resource | Free tier? | Pricing model | Est. $/month at this project's scale |
|---|---|---|---|
| Storage Account (`Standard_LRS`) | No account-level free tier | ~$0.018/GB stored (Hot) + ~$0.05/10K write ops + egress | ~$0.10–$1 |
| Cosmos DB (**provisioned, free tier**, as configured) | **Yes** — 1,000 RU/s + 25 GB storage, forever free (not a monthly quota) | Standard provisioned-throughput rates for anything above 1,000 RU/s | **$0** at up to 10 companies (see capacity analysis below) |
| Key Vault (standard) | No free tier, but priced per-operation | ~$0.03 per 10,000 operations | <$0.10 |
| Log Analytics workspace | Yes — first 5 GB/month ingestion free | ~$2.76–$4.30/GB beyond 5 GB | $0 at this volume |
| Application Insights (workspace-based) | Bills through the Log Analytics workspace, no separate charge | (included above) | $0 (already counted) |
| Function App (Flex Consumption, FC1) | Yes — 1,000,000 executions + 400,000 GB-s free **every month** (this one genuinely resets monthly, unlike Cosmos's standing grant) | ~$0.20/million executions beyond the free grant | $0 — this pipeline is ~50 invocations per scrape run |
| Managed identity + role assignment | Free | N/A | $0 |

**Total estimate: roughly $0.10–$1.10/month**, essentially just Storage Account transaction/egress
costs — Cosmos DB now costs $0 thanks to the free tier (see below).

### Cosmos DB: free tier capacity analysis (up to 10 companies/day)

`cosmos.bicep` provisions the account with `enableFreeTier: true` and a **shared, database-level,
manual** throughput of exactly `1000` RU/s (the `sharedThroughputRuPerSecond` parameter) — all 3
containers (`job-postings`, `job-observations`, `monthly-metrics`) draw from this one shared pool
rather than each requiring their own dedicated minimum (which would have exceeded the free grant:
3 containers × 400 RU/s minimum each = 1,200 RU/s).

**What "1,000 RU/s" means:** a *continuously reserved throughput rate*, not a monthly quota — it
does not reset monthly (contrast with the Function App row above, which genuinely does). Unused
capacity in any given second is not banked for later; demand above 1,000 RU/s in a given second is
throttled (`429`, auto-retried by the SDK) rather than billed, unless additional RU/s is
provisioned.

**Per-posting RU cost through this pipeline** (Persistence `findById` read + 2 upserts, Metrics
read + upsert): **~35–40 RU/posting**. At a generous estimate of 50 postings/company/day × 10
companies = 500 postings/day, even a tight 60-second processing burst is only
`500 × 40 RU ÷ 60s ≈ 330 RU/s` — comfortably under 1,000 RU/s, with headroom to spare. Two
structural factors widen that margin further: every Kafka topic has **1 partition** (per the root
README's Confluent topic reference), so consumers process messages strictly sequentially rather
than in parallel bursts; and `JOB_SCRAPER_REQUEST_DELAY=PT0.3S` throttles scraping itself, spacing
ingestion out over time.

**If company count or posting volume grows further:** watch the Cosmos account's **Insights →
Throughput** metrics in the Azure Portal (or a Cost Management/Monitor alert on normalized RU
consumption) for sustained values approaching 1,000 RU/s. Raising `sharedThroughputRuPerSecond`
above 1,000 is a one-parameter change, but note that only the *first* 1,000 RU/s stays free — RU/s
provisioned above that is billed at standard rates (~$0.008/hour per 100 RU/s, roughly $6/month
per additional 100 RU/s held around the clock).

**One-per-subscription constraint:** Azure allows only one Cosmos DB account with
`enableFreeTier: true` per subscription. If this subscription already hosts another Cosmos free-
tier account, this deployment will fail at `enableFreeTier: true` — set
`sharedThroughputRuPerSecond` and switch to non-free provisioned throughput (or back to
serverless) in that case.

**Capacity mode is a one-time decision:** Cosmos DB accounts cannot be converted between
serverless and provisioned throughput after creation — switching later requires a new account and
a data migration. This module intentionally decided on provisioned + free tier now, before any
real deployment or data exists.

### Not part of this Bicep, but part of the real monthly bill

- **Confluent Cloud** (`shared-kafka-dev-us-central1`) already exists and is billed separately by
  Confluent (cluster/CKU + throughput) — the Terraform in `infra/confluent/` only manages
  topics/ACLs/service accounts on it, all free operations on Confluent's side.
- **Resend** — the free tier (100 emails/day, 3,000/month) covers current volume; $0.

