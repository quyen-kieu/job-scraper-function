@description('Name of the Function App.')
param functionAppName string

@description('Name of the App Service Plan (Flex Consumption).')
param planName string

@description('Azure region.')
param location string

@description('Name of the storage account backing AzureWebJobsStorage (the same account created by modules/storage.bicep).')
param storageAccountName string

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
      linuxFxVersion: 'Java|21'
      appSettings: concat(appSettings, [
        {
          name: 'AzureWebJobsStorage__accountName'
          value: storageAccountExisting.name
        }
      ])
    }
    functionAppConfig: {
      runtime: {
        name: 'java'
        version: '21'
      }
    }
  }
}

output principalId string = functionApp.identity.principalId
output name string = functionApp.name
output defaultHostName string = functionApp.properties.defaultHostName

