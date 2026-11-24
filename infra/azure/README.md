# Azure Bicep — Runbook

Provisions the deployment boundary described in the [architecture](../../docs/architecture.md):
one Function App and Flex Consumption plan, one Storage Account, one
Cosmos DB account (provisioned throughput, free tier, 3 containers), one Key Vault, and
Application Insights/Log Analytics for monitoring.

## Prerequisites

- Azure CLI >= 2.60 with the `bicep` extension (`az bicep install`).
- Contributor access to the target resource group and the role-assignment permissions described
  in the [CI/CD reference](../../.github/workflows/README.md).

## Validate (no changes made)

```powershell
az bicep build --file infra/azure/main.bicep
az deployment group what-if `
  --resource-group "<resource-group>" `
  --template-file infra/azure/main.bicep `
  --parameters infra/azure/parameters.dev.json `
  --parameters kafkaBootstrapServers="<pkc-xxxxx...:9092>" resendFromEmail="<sender>" resendToEmail="<recipient>"
```

## Deploy

> The Kafka bootstrap server and Resend email addresses are never committed to `parameters.*.json`; pass them with `--parameters` as shown (CI reads them from GitHub secrets).
> Azure what-if output can include app-setting values. Run these commands on a trusted machine and
> do not publish their output; do not enter real values in commands saved to shell history.

The deployment workflow provides an `apply-azure` job (gated
behind the `production` GitHub Environment's required reviewer). The manual commands below remain
useful for local/one-off testing against `dev`, or for diagnosing a failed pipeline run.

```powershell
az deployment group create `
  --resource-group "<resource-group>" `
  --template-file infra/azure/main.bicep `
  --parameters infra/azure/parameters.dev.json `
  --parameters kafkaBootstrapServers="<pkc-xxxxx...:9092>" resendFromEmail="<sender>" resendToEmail="<recipient>"
```

## Setting real secret values

`main.bicep` only creates the Key Vault secrets it can derive itself (`AzureWebJobsStorage`,
`azure-storage-connection-string`, `azure-cosmos-key`). It deliberately does **not** declare
`kafka-username`, `kafka-password`, or `resend-api-key`: earlier versions created them with empty
placeholder values, so every `az deployment group create` silently overwrote the real secrets with
"" and broke the Kafka triggers. In CI, `deploy.yml`'s deploy-app job writes them from GitHub
secrets. For a manual deployment, set them yourself after the first `create`:

```powershell
$vaultName = Read-Host "Key Vault name"
$secretNames = @("kafka-username", "kafka-password", "resend-api-key")
foreach ($secretName in $secretNames) {
    $secureValue = Read-Host "Value for $secretName" -AsSecureString
    $value = [System.Net.NetworkCredential]::new('', $secureValue).Password
    try {
        az keyvault secret set --vault-name $vaultName --name $secretName --value $value --output none
        if ($LASTEXITCODE -ne 0) { throw "Failed to set $secretName" }
    }
    finally {
        Remove-Variable value, secureValue -ErrorAction SilentlyContinue
    }
}
```

The values are passed to Azure CLI as process arguments; run this on a trusted machine and do not
log or share the commands or environment. The `--output none` option avoids printing secret values.
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

## Scope decisions

- Cosmos and Blob Storage keep key/connection-string auth (Key Vault only changes *where* the
  secret is stored, not *how* the app authenticates). Adopting managed-identity/RBAC-based Cosmos
  and Storage access is a hardening follow-up requiring a Java code change.
- `pom.xml` uses generic local defaults for `functionAppName`, `appServicePlanName`, and
  `azureResourceGroup`. CI overrides `functionAppName` when packaging and deploys with
  `az functionapp deployment source config-zip` instead of the plugin's `deploy` goal. Manual
  plugin deployment would require the target values and `azureRegion` as Maven properties, and
  the plugin's deploy goal is not reliable for this Flex Consumption configuration; use the
  [CI/CD workflow](../../.github/workflows/README.md) instead.

## Cost considerations

The Cosmos module requests account-level free tier and provisions a shared, manual
database throughput of 1,000 RU/s across the three containers. Microsoft documents an allowance
of 1,000 RU/s and 25 GB per free-tier account, with at most one such account per subscription.
Check eligibility and the account's status before deployment; if free tier is unavailable, the
template must be changed before provisioning a non-free account. Provisioned throughput above
the allowance, storage beyond it, and other features can incur charges. See the
[Cosmos free-tier documentation](https://learn.microsoft.com/en-us/azure/cosmos-db/free-tier).

RU/s is a throughput rate, not a daily quota. Monitor actual request units and throttling; cost and
capacity depend on document sizes, reads, writes, and workload. The storage account, Functions,
Key Vault, monitoring, Confluent Cloud, and Resend may also incur charges, including outside
their respective free allowances. Use the
[Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/), check provider
pricing, and configure a budget rather than assuming a particular monthly total.

An existing Cosmos account cannot be converted between serverless and provisioned modes; a mode
change requires a new account and data migration.
