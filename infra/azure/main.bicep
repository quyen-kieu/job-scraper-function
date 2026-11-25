@description('Deployment environment name, e.g. dev or prod. Used to suffix resource names.')
param environmentName string

@description('Azure region for all resources.')
param location string = 'centralus'

@description('Base name used to derive resource names (Function App, plan, storage, Cosmos, Key Vault, monitoring).')
param baseName string = 'job-scraper-function'

@description('Cosmos SQL database name.')
param cosmosDatabaseName string = 'job-scraper'

@description('Cosmos container name for current job postings.')
param cosmosPostingsContainer string = 'job-postings'

@description('Cosmos container name for daily job observations.')
param cosmosObservationsContainer string = 'job-observations'

@description('Cosmos container name for monthly metrics.')
param cosmosMetricsContainer string = 'monthly-metrics'

@description('Cosmos container for temporary description handoffs.')
param cosmosDescriptionHandoffContainer string = 'description-handoffs'

@description('Azure AD tenant ID for Key Vault RBAC.')
param tenantId string = subscription().tenantId

@description('Object ID of the GitHub Actions OIDC service principal (deploy.yml\'s azure/login identity). Granted Key Vault Secrets Officer so deploy-app can write real secret values post-deployment. Not a secret itself (an object ID has no standalone access), so it is a plain, non-@secure() parameter sourced from a GitHub Variable.')
param cicdServicePrincipalObjectId string = ''

@description('Kafka bootstrap servers (Confluent Cloud), e.g. pkc-xxxxx.region.provider.confluent.cloud:9092. Supplied at deploy time from the KAFKA_BOOTSTRAP_SERVERS GitHub secret, never committed.')
param kafkaBootstrapServers string

// NOTE: kafka-username, kafka-password and resend-api-key are deliberately NOT Bicep parameters or
// Bicep-managed secrets. They are owned exclusively by deploy.yml's "Sync real secret values into
// Key Vault" step. Previously Bicep declared them with empty placeholder values, which silently
// overwrote the real secrets with "" on every `az deployment group create`, leaving the Functions
// host in Error state ("Confluent.Kafka: sasl.username and sasl.password must be set.").

@description('Resend "from" email address, e.g. send@example.com. Supplied at deploy time from the RESEND_FROM_EMAIL GitHub secret, never committed.')
param resendFromEmail string

@description('Resend "from" display name.')
param resendFromName string = 'Job Scraper'

@description('Resend "to" email address for the daily summary. Supplied at deploy time from the RESEND_TO_EMAIL GitHub secret, never committed.')
param resendToEmail string

@description('Subject prefix for the daily summary email.')
param resendSubjectPrefix string = 'Job Scraper Daily Summary'

@description('Cron expression for the daily scrape trigger.')
param jobScraperDailyCron string = '0 0 3 * * *'

@description('McKesson Workday state/province facet ID.')
param mckessonStateFacetId string = 'fc77e3a1ab36487f9646d14f7242dd77'

@description('McKesson Workday job family group facet ID.')
param mckessonJobFamilyFacetId string = '29ce91b0412201aafb7bf4bd4f169117'

@description('McKesson Workday country facet ID.')
param mckessonCountryFacetId string = 'bc33aa3152ec42d4995f4791a106ed09'

@description('Frost Bank Workday job family facet ID.')
param frostBankJobFamilyFacetId string = '3a9cae9f1324015f492308c79dc96d35'

@description('Frost Bank Workday location hierarchy facet IDs.')
param frostBankLocationFacetIds array = [
  '8fe73009103a424c89e0c6c010a6d8f3'
  '58c99fe2f997418f8c54b3ebcf1b6a5d'
]

@description('Citi Workday job family group facet ID.')
param citiJobFamilyFacetId string = 'e32326e1708d01575bddff0c120102c1'

@description('Citi Workday worker subtype facet ID.')
param citiWorkerSubtypeFacetId string = 'e32326e1708d01289a50257249019cdd'

@description('Citi Workday country facet ID.')
param citiCountryFacetId string = 'bc33aa3152ec42d4995f4791a106ed09'

@description('Citi Workday state/province facet ID.')
param citiStateFacetId string = 'fc77e3a1ab36487f9646d14f7242dd77'

@description('Fannie Mae Workday remote type facet ID.')
param fannieMaeRemoteTypeFacetId string = 'bf1dc34adb121001727a83cb88650000'

@description('Fannie Mae Workday job family group facet ID.')
param fannieMaeJobFamilyFacetId string = 'bf1dc34adb12100175ce6a6921f70000'

@description('Fannie Mae Workday location facet ID.')
param fannieMaeLocationFacetId string = '464222325cc3100210ee7e4b71320000'

@description('CVS Workday job family group facet ID.')
param cvsJobFamilyFacetId string = 'e65dbadf6a50100168ed86fe4cf50001'

@description('CVS Workday location facet IDs.')
param cvsLocationFacetIds array = [
  '69b7f7384b5e1008e9c744772f180000'
  'e75b3cfda6d41000edbef9d1f17b0000'
  'e75b3cfda6d41000edbdcbb1c2b50000'
  'e75b3cfda6d41000edbc9e0488440000'
  'e75b3cfda6d41000edc098b35b130000'
  'e75b3cfda6d41000edc32657f9160000'
]

var regionCode = location == 'centralus' ? 'cus' : take(replace(toLower(location), ' ', ''), 3)
var storageAccountName = toLower('${take(replace(baseName, '-', ''), 10)}st${environmentName}${regionCode}')
var cosmosAccountName = toLower('${take(replace(baseName, '-', ''), 14)}-cosmos-${environmentName}-${regionCode}')
var keyVaultName = toLower('${take(replace(baseName, '-', ''), 12)}kv${environmentName}${regionCode}')
var functionAppName = toLower('${take(replace(baseName, '-', ''), 10)}-${environmentName}-${regionCode}')
var planName = toLower('${take(replace(baseName, '-', ''), 10)}-plan-${environmentName}-${regionCode}')
var logAnalyticsWorkspaceName = toLower('${take(replace(baseName, '-', ''), 10)}-logs-${environmentName}-${regionCode}')
var appInsightsName = toLower('${take(replace(baseName, '-', ''), 10)}-ai-${environmentName}-${regionCode}')

module storage 'modules/storage.bicep' = {
  name: 'storageDeploy'
  params: {
    storageAccountName: storageAccountName
    location: location
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
    descriptionHandoffContainerName: cosmosDescriptionHandoffContainer
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

// Key Vault + the secrets Bicep can derive itself (storage connection string, Cosmos key).
// kafka-username / kafka-password / resend-api-key are intentionally absent: they are written
// only by deploy.yml's deploy-app job (from GitHub secrets) so a Bicep apply can never clobber
// them. The app settings below still reference them by URI; on a brand-new environment those
// references stay unresolved until the first deploy-app run populates the vault.
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
      'azure-cosmos-key': cosmos.outputs.primaryKey
    }
  }
}

var vaultUri = keyVault.outputs.vaultUri
var kafkaUsernameSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/kafka-username/)'
var kafkaPasswordSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/kafka-password/)'
var storageConnectionStringSecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/AzureWebJobsStorage/)'
var cosmosKeySecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/azure-cosmos-key/)'
var resendApiKeySecretUri = '@Microsoft.KeyVault(SecretUri=${vaultUri}secrets/resend-api-key/)'

var appSettings = [
  // NOTE: FUNCTIONS_WORKER_RUNTIME and FUNCTIONS_EXTENSION_VERSION are deliberately NOT set here.
  // Flex Consumption plans reject both app settings outright (BadRequest: "... for Flex
  // Consumption sites is invalid. Please remove or rename it before retrying.") — the runtime
  // and version are instead declared once via functionAppConfig.runtime (see function-app.bicep).
  { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: monitoring.outputs.connectionString }
  { name: 'JOB_SCRAPER_DAILY_CRON', value: jobScraperDailyCron }
  { name: 'JOB_SCRAPER_USER_AGENT', value: 'job-scraper/0.1' }
  { name: 'JOB_SCRAPER_REQUEST_TIMEOUT', value: 'PT30S' }
  { name: 'JOB_SCRAPER_MAX_RETRIES', value: '3' }
  { name: 'JOB_SCRAPER_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_COMPANIES_0_ID', value: 'mckesson' }
  { name: 'JOB_SCRAPER_COMPANIES_0_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_MCKESSON_STATE_FACET_ID', value: mckessonStateFacetId }
  { name: 'JOB_SCRAPER_MCKESSON_JOB_FAMILY_FACET_ID', value: mckessonJobFamilyFacetId }
  { name: 'JOB_SCRAPER_MCKESSON_COUNTRY_FACET_ID', value: mckessonCountryFacetId }
  // GM Financial: "software development" search on its Oracle Recruiting Cloud site
  // (see GmFinancialScraperConfiguration). Each company gets its own daily summary email.
  { name: 'JOB_SCRAPER_COMPANIES_1_ID', value: 'gm-financial' }
  { name: 'JOB_SCRAPER_COMPANIES_1_ENABLED', value: 'true' }
  // First Citizens Bank: Information Technology jobs, newest first.
  { name: 'JOB_SCRAPER_COMPANIES_2_ID', value: 'first-citizens' }
  { name: 'JOB_SCRAPER_COMPANIES_2_ENABLED', value: 'true' }
  // Frost Bank: Technology & Digital and selected Workday locations.
  { name: 'JOB_SCRAPER_COMPANIES_3_ID', value: 'frost-bank' }
  { name: 'JOB_SCRAPER_COMPANIES_3_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_FROST_BANK_JOB_FAMILY_FACET_ID', value: frostBankJobFamilyFacetId }
  { name: 'JOB_SCRAPER_FROST_BANK_LOCATION_FACET_IDS', value: join(frostBankLocationFacetIds, ',') }
  // CVS Health: selected Workday job family and locations.
  { name: 'JOB_SCRAPER_COMPANIES_4_ID', value: 'cvs' }
  { name: 'JOB_SCRAPER_COMPANIES_4_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_CVS_JOB_FAMILY_FACET_ID', value: cvsJobFamilyFacetId }
  { name: 'JOB_SCRAPER_CVS_LOCATION_FACET_IDS', value: join(cvsLocationFacetIds, ',') }
  { name: 'JOB_SCRAPER_COMPANIES_5_ID', value: 'citi' }
  { name: 'JOB_SCRAPER_COMPANIES_5_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_CITI_JOB_FAMILY_FACET_ID', value: citiJobFamilyFacetId }
  { name: 'JOB_SCRAPER_CITI_WORKER_SUBTYPE_FACET_ID', value: citiWorkerSubtypeFacetId }
  { name: 'JOB_SCRAPER_CITI_COUNTRY_FACET_ID', value: citiCountryFacetId }
  { name: 'JOB_SCRAPER_CITI_STATE_FACET_ID', value: citiStateFacetId }
  { name: 'JOB_SCRAPER_COMPANIES_6_ID', value: 'fannie-mae' }
  { name: 'JOB_SCRAPER_COMPANIES_6_ENABLED', value: 'true' }
  { name: 'JOB_SCRAPER_FANNIE_MAE_REMOTE_TYPE_FACET_ID', value: fannieMaeRemoteTypeFacetId }
  { name: 'JOB_SCRAPER_FANNIE_MAE_JOB_FAMILY_FACET_ID', value: fannieMaeJobFamilyFacetId }
  { name: 'JOB_SCRAPER_FANNIE_MAE_LOCATION_FACET_ID', value: fannieMaeLocationFacetId }
  { name: 'JOB_SCRAPER_MAX_SEARCH_PAGES_PER_RUN', value: '3' }
  { name: 'JOB_SCRAPER_REQUEST_DELAY', value: 'PT0.3S' }
  { name: 'RAW_STORAGE_MODE', value: 'cosmos' }
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
  { name: 'AZURE_COSMOS_ENABLED', value: 'true' }
  { name: 'AZURE_COSMOS_ENDPOINT', value: cosmos.outputs.endpoint }
  { name: 'AZURE_COSMOS_KEY', value: cosmosKeySecretUri }
  { name: 'AZURE_COSMOS_DATABASE_NAME', value: cosmosDatabaseName }
  { name: 'AZURE_COSMOS_POSTINGS_CONTAINER', value: cosmosPostingsContainer }
  { name: 'AZURE_COSMOS_OBSERVATIONS_CONTAINER', value: cosmosObservationsContainer }
  { name: 'AZURE_COSMOS_METRICS_CONTAINER', value: cosmosMetricsContainer }
  { name: 'AZURE_COSMOS_DESCRIPTION_HANDOFF_CONTAINER', value: cosmosDescriptionHandoffContainer }
  { name: 'RESEND_ENABLED', value: 'true' }
  { name: 'RESEND_API_KEY', value: resendApiKeySecretUri }
  { name: 'RESEND_FROM_EMAIL', value: resendFromEmail }
  { name: 'RESEND_FROM_NAME', value: resendFromName }
  { name: 'RESEND_TO_EMAIL', value: resendToEmail }
  { name: 'RESEND_SUBJECT_PREFIX', value: resendSubjectPrefix }
  { name: 'AzureWebJobsStorage', value: storageConnectionStringSecretUri }
]

module functionApp 'modules/function-app.bicep' = {
  name: 'functionAppDeploy'
  params: {
    functionAppName: functionAppName
    planName: planName
    location: location
    storageAccountName: storage.outputs.storageAccountName
    deploymentContainerName: storage.outputs.deploymentContainerName
    appSettings: appSettings
  }
}

// Built-in "Storage Blob Data Owner" role definition id. Flex Consumption's deployment storage
// (functionAppConfig.deployment.storage, configured with SystemAssignedIdentity auth in
// function-app.bicep) requires the Function App's managed identity to be able to read/write the
// deployment package blob in storage.outputs.deploymentContainerName — without this, every
// `az functionapp deploy` fails with a consistent "502 ... invalid response ... gateway" from the
// SCM/Kudu site, since the platform can authenticate neither to read nor write the package blob.
var storageBlobDataOwnerRoleId = 'b7e6dc6d-f1e8-4753-8033-0f276bb0955b'

resource existingStorageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' existing = {
  name: storageAccountName
  dependsOn: [
    storage
  ]
}

resource functionAppStorageRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(existingStorageAccount.id, functionAppName, storageBlobDataOwnerRoleId)
  scope: existingStorageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', storageBlobDataOwnerRoleId)
    principalId: functionApp.outputs.principalId
    principalType: 'ServicePrincipal'
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

// Built-in "Key Vault Secrets Officer" role definition id — grants read/write/delete on secrets
// (not just read, unlike Secrets User above), needed by deploy.yml's deploy-app job to run
// `az keyvault secret set` for kafka-username/kafka-password/resend-api-key post-deployment.
// Only assigned when the parameter is actually supplied (skip-if-empty via a conditional
// resource), so this stays a no-op for any deployment that doesn't pass a CI/CD identity.
var keyVaultSecretsOfficerRoleId = 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'

resource cicdKeyVaultRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(cicdServicePrincipalObjectId)) {
  name: guid(existingVault.id, cicdServicePrincipalObjectId, keyVaultSecretsOfficerRoleId)
  scope: existingVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', keyVaultSecretsOfficerRoleId)
    principalId: cicdServicePrincipalObjectId
    principalType: 'ServicePrincipal'
  }
}

output functionAppName string = functionApp.outputs.name
output functionAppDefaultHostName string = functionApp.outputs.defaultHostName
output keyVaultName string = keyVault.outputs.vaultName
output cosmosEndpoint string = cosmos.outputs.endpoint
