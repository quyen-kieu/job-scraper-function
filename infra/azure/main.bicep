@description('Deployment environment name, e.g. dev or prod. Used to suffix resource names.')
param environmentName string

@description('Azure region for all resources.')
param location string = 'westus'

@description('Base name used to derive resource names (Function App, plan, storage, Cosmos, Key Vault, monitoring).')
param baseName string = 'job-scraper-function'

@description('Blob container name for raw scrape artifacts.')
param storageContainerName string = 'job-scraper-raw'

@description('Cosmos SQL database name.')
param cosmosDatabaseName string = 'job-scraper'

@description('Cosmos container name for current job postings.')
param cosmosPostingsContainer string = 'job-postings'

@description('Cosmos container name for daily job observations.')
param cosmosObservationsContainer string = 'job-observations'

@description('Cosmos container name for monthly metrics.')
param cosmosMetricsContainer string = 'monthly-metrics'

@description('Azure AD tenant ID for Key Vault RBAC.')
param tenantId string = subscription().tenantId

@description('Kafka bootstrap servers (Confluent Cloud), e.g. pkc-xxxxx.region.provider.confluent.cloud:9092.')
param kafkaBootstrapServers string

@description('Kafka SASL username placeholder. Real value is set directly in Key Vault out-of-band via `az keyvault secret set` (empty default per linter guidance for secure parameters).')
@secure()
param kafkaUsernamePlaceholder string = ''

@description('Kafka SASL password placeholder; see kafkaUsernamePlaceholder.')
@secure()
param kafkaPasswordPlaceholder string = ''

@description('Resend API key placeholder; see kafkaUsernamePlaceholder.')
@secure()
param resendApiKeyPlaceholder string = ''

@description('Resend "from" email address, e.g. send@quyenkieu.com.')
param resendFromEmail string

@description('Resend "from" display name.')
param resendFromName string = 'Job Scraper'

@description('Resend "to" email address for the daily summary.')
param resendToEmail string

@description('Subject prefix for the daily summary email.')
param resendSubjectPrefix string = 'Job Scraper Daily Summary'

@description('Cron expression for the daily scrape trigger.')
param jobScraperDailyCron string = '0 0 3 * * *'

var suffix = '-${environmentName}'
var storageAccountName = replace(toLower('${baseName}st${environmentName}'), '-', '')
var cosmosAccountName = toLower('${baseName}-cosmos${suffix}')
var keyVaultName = toLower('${baseName}-kv${suffix}')
var functionAppName = '${baseName}${suffix}'
var planName = '${baseName}-plan${suffix}'
var logAnalyticsWorkspaceName = '${baseName}-logs${suffix}'
var appInsightsName = '${baseName}-ai${suffix}'

module storage 'modules/storage.bicep' = {
  name: 'storageDeploy'
  params: {
    storageAccountName: storageAccountName
    location: location
    containerName: storageContainerName
  }
}

module cosmos 'modules/cosmos.bicep' = {
  name: 'cosmosDeploy'
  params: {
    accountName: cosmosAccountName
    location: location
    databaseName: cosmosDatabaseName
    postingsContainerName: cosmosPostingsContainer
    observationsContainerName: cosmosObservationsContainer
    metricsContainerName: cosmosMetricsContainer
  }
}

module monitoring 'modules/monitoring.bicep' = {
  name: 'monitoringDeploy'
  params: {
    logAnalyticsWorkspaceName: logAnalyticsWorkspaceName
    appInsightsName: appInsightsName
    location: location
  }
}

// Key Vault secret placeholders. Real production values are set with `az keyvault secret set`
// (or a future Stage 8 automated step) and are never derived from Bicep parameters in practice —
// these placeholders only exist so the secret *resources* (name + access policy) are reviewable.
//
// NOTE: this module deliberately does not depend on the functionApp module (see keyvault.bicep
// comment) — the Key Vault Secrets User role assignment is granted below, after both modules
// exist, to avoid a circular module dependency (functionApp needs this vault's URI; the role
// assignment needs functionApp's principal id).
module keyVault 'modules/keyvault.bicep' = {
  name: 'keyVaultDeploy'
  params: {
    keyVaultName: keyVaultName
    location: location
    tenantId: tenantId
    secrets: {
      AzureWebJobsStorage: storage.outputs.connectionString
      'kafka-username': kafkaUsernamePlaceholder
      'kafka-password': kafkaPasswordPlaceholder
      'azure-storage-connection-string': storage.outputs.connectionString
      'azure-cosmos-key': cosmos.outputs.primaryKey
      'resend-api-key': resendApiKeyPlaceholder
    }
  }
}

var vaultUri = keyVault.outputs.vaultUri
var kafkaUsernameSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/kafka-username/)'
var kafkaPasswordSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/kafka-password/)'
var storageConnectionStringSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/azure-storage-connection-string/)'
var cosmosKeySecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/azure-cosmos-key/)'
var resendApiKeySecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/resend-api-key/)'

var appSettings = [
  { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
  { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
  { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: monitoring.outputs.connectionString }
  { name: 'JOB_SCRAPER_DAILY_CRON', value: jobScraperDailyCron }
  { name: 'JOB_SCRAPER_USER_AGENT', value: 'job-scraper/0.1' }
  { name: 'JOB_SCRAPER_REQUEST_TIMEOUT', value: 'PT30S' }
  { name: 'JOB_SCRAPER_MAX_RETRIES', value: '3' }
  { name: 'JOB_SCRAPER_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_COMPANIES_0_ID', value: 'mckesson' }
  { name: 'JOB_SCRAPER_COMPANIES_0_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_MAX_SEARCH_PAGES_PER_RUN', value: '3' }
  { name: 'JOB_SCRAPER_MAX_DETAIL_PAGES_PER_RUN', value: '10' }
  { name: 'JOB_SCRAPER_REQUEST_DELAY', value: 'PT0.3S' }
  { name: 'RAW_STORAGE_MODE', value: 'blob' }
  { name: 'KAFKA_ENABLED', value: 'true' }
  { name: 'KAFKA_BOOTSTRAP_SERVERS', value: kafkaBootstrapServers }
  { name: 'KAFKA_SECURITY_PROTOCOL', value: 'SASL_SSL' }
  { name: 'KAFKA_SASL_MECHANISM', value: 'PLAIN' }
  { name: 'KAFKA_SCRAPE_COMMANDS_TOPIC', value: 'job.scrape.commands' }
  { name: 'KAFKA_SCRAPE_COMPLETED_TOPIC', value: 'job.scrape.completed' }
  { name: 'KAFKA_POSTINGS_NORMALIZED_TOPIC', value: 'job.postings.normalized' }
  { name: 'KAFKA_METRICS_MONTHLY_TOPIC', value: 'job.metrics.monthly' }
  { name: 'KAFKA_NOTIFICATIONS_DAILY_TOPIC', value: 'notifications.daily' }
  { name: 'KAFKA_SCRAPER_CONSUMER_GROUP', value: 'job-scraper-scraper-consumer-group' }
  { name: 'KAFKA_NORMALIZER_CONSUMER_GROUP', value: 'job-scraper-normalizer-consumer-group' }
  { name: 'KAFKA_PERSISTENCE_CONSUMER_GROUP', value: 'job-scraper-persistence-consumer-group' }
  { name: 'KAFKA_METRICS_CONSUMER_GROUP', value: 'job-scraper-metrics-consumer-group' }
  { name: 'KAFKA_EMAIL_CONSUMER_GROUP', value: 'job-scraper-email-consumer-group' }
  { name: 'KAFKA_USERNAME', value: kafkaUsernameSecretUri }
  { name: 'KAFKA_PASSWORD', value: kafkaPasswordSecretUri }
  { name: 'AZURE_STORAGE_ENABLED', value: 'true' }
  { name: 'AZURE_STORAGE_CONNECTION_STRING', value: storageConnectionStringSecretUri }
  { name: 'AZURE_STORAGE_CONTAINER_NAME', value: storageContainerName }
  { name: 'AZURE_COSMOS_ENABLED', value: 'true' }
  { name: 'AZURE_COSMOS_ENDPOINT', value: cosmos.outputs.endpoint }
  { name: 'AZURE_COSMOS_KEY', value: cosmosKeySecretUri }
  { name: 'AZURE_COSMOS_DATABASE_NAME', value: cosmosDatabaseName }
  { name: 'AZURE_COSMOS_POSTINGS_CONTAINER', value: cosmosPostingsContainer }
  { name: 'AZURE_COSMOS_OBSERVATIONS_CONTAINER', value: cosmosObservationsContainer }
  { name: 'AZURE_COSMOS_METRICS_CONTAINER', value: cosmosMetricsContainer }
  { name: 'RESEND_ENABLED', value: 'true' }
  { name: 'RESEND_API_KEY', value: resendApiKeySecretUri }
  { name: 'RESEND_FROM_EMAIL', value: resendFromEmail }
  { name: 'RESEND_FROM_NAME', value: resendFromName }
  { name: 'RESEND_TO_EMAIL', value: resendToEmail }
  { name: 'RESEND_SUBJECT_PREFIX', value: resendSubjectPrefix }
]

module functionApp 'modules/function-app.bicep' = {
  name: 'functionAppDeploy'
  params: {
    functionAppName: functionAppName
    planName: planName
    location: location
    storageAccountName: storage.outputs.storageAccountName
    appSettings: appSettings
  }
}

// Built-in "Key Vault Secrets User" role definition id.
var keyVaultSecretsUserRoleId = '4633458b-17de-408a-b874-0445c86b69e6'

// NOTE: `existing` resource lookup and the guid() name below deliberately use the plain
// `keyVaultName` / `functionAppName` variables (known at authoring time from parameters), not
// `keyVault.outputs.vaultName` / `functionApp.outputs.principalId`. ARM requires a
// roleAssignments resource's `name` and `scope` to be calculable at the start of deployment;
// referencing another module's output there fails with BCP120. `principalId` inside `properties`
// has no such restriction, so it safely uses the runtime module output. `dependsOn` makes the
// ordering explicit since the implicit output-reference dependency edges no longer exist for
// `name`/`scope`.
resource existingVault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: keyVaultName
  dependsOn: [
    keyVault
  ]
}

resource functionAppKeyVaultRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(existingVault.id, functionAppName, keyVaultSecretsUserRoleId)
  scope: existingVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', keyVaultSecretsUserRoleId)
    principalId: functionApp.outputs.principalId
    principalType: 'ServicePrincipal'
  }
}

output functionAppName string = functionApp.outputs.name
output functionAppDefaultHostName string = functionApp.outputs.defaultHostName
output keyVaultName string = keyVault.outputs.vaultName
output cosmosEndpoint string = cosmos.outputs.endpoint







