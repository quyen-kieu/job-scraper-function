@description('Name of the Function App.')
param functionAppName string

@description('Name of the App Service Plan (Flex Consumption).')
param planName string

@description('Azure region.')
param location string

@description('Name of the storage account backing AzureWebJobsStorage (the same account created by modules/storage.bicep).')
param storageAccountName string

@description('Blob container name Flex Consumption uses to store the deployed Function App package (must already exist — created by modules/storage.bicep).')
param deploymentContainerName string

@description('Full list of application settings (name/value pairs). Secret values are expected to already be Key Vault reference strings, e.g. @Microsoft.KeyVault(SecretUri=...).')
param appSettings array

resource storageAccountExisting 'Microsoft.Storage/storageAccounts@2023-01-01' existing = {
  name: storageAccountName
}

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: planName
  location: location
  kind: 'functionapp,linux'
  sku: {
    tier: 'FlexConsumption'
    name: 'FC1'
  }
  properties: {
    reserved: true
  }
}

resource functionApp 'Microsoft.Web/sites@2023-12-01' = {
  name: functionAppName
  location: location
  kind: 'functionapp,linux'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      appSettings: concat(appSettings, [
        {
          name: 'AzureWebJobsStorage__accountName'
          value: storageAccountExisting.name
        }
      ])
    }
    functionAppConfig: {
      deployment: {
        storage: {
          // NOTE: this `value` must be the deployment *blob container URL*, not a storage
          // account connection string. Setting a connection string here (the original, buggy
          // config) silently broke every `az functionapp deploy` call with a consistent,
          // non-transient "502 - Web server received an invalid response while acting as a
          // gateway" from the SCM/Kudu site — the platform's deployment pipeline couldn't
          // resolve where to put the package. Using SystemAssignedIdentity auth here (paired
          // with the Storage Blob Data Owner role assignment granted to this app's identity in
          // main.bicep) also avoids any dependency on Key Vault reference resolution timing for
          // a connection-string app setting, which is both simpler and more secure than
          // StorageAccountConnectionString auth would have been.
          type: 'BlobContainer'
          value: 'https://${storageAccountExisting.name}.blob.${environment().suffixes.storage}/${deploymentContainerName}'
          authentication: {
            type: 'SystemAssignedIdentity'
          }
        }
      }
      runtime: {
        name: 'java'
        version: '21'
      }
      scaleAndConcurrency: {
        // 512 MB (the Flex Consumption default) is too little for this app: the packaged
        // artifact is a ~70MB Spring Boot fat-jar (Cosmos SDK + Kafka clients + Spring Boot
        // itself), and deploying/starting it in a 512MB container reliably OOM-crashed the
        // instance mid-deploy. That crash is what caused every `az functionapp deploy` to fail
        // with a consistent, immediate "502 - invalid response ... gateway" from Kudu (confirmed
        // by: `/api/deployments` staying empty even after a "successful" POST, and the identical
        // 502 reproducing when POSTing directly to Kudu's /api/zipdeploy with a valid AAD token,
        // bypassing the CLI entirely). Bumping to 2048MB (manually verified live against this
        // exact app with `az functionapp scale config set --instance-memory 2048`) is the fix.
        instanceMemoryMB: 2048
        maximumInstanceCount: 4
      }
    }
  }
}

output principalId string = functionApp.identity.principalId
output name string = functionApp.name
output defaultHostName string = functionApp.properties.defaultHostName
