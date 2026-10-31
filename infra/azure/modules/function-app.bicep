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
        // 512 MB keeps execution-time (GB-s) cost minimal. Instance memory has no effect on
        // deployment: the deploy-app 502s were caused by `az functionapp deploy` uploading with
        // Content-Type application/octet-stream (rejected by Flex Consumption with 415), not by
        // memory — see the deploy step comment in .github/workflows/deploy.yml.
        instanceMemoryMB: 512
        maximumInstanceCount: 4
      }
    }
  }
}

output principalId string = functionApp.identity.principalId
output name string = functionApp.name
output defaultHostName string = functionApp.properties.defaultHostName
