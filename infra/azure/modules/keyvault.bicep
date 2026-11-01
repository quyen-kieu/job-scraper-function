@description('Name of the Key Vault.')
param keyVaultName string

@description('Azure region.')
param location string

@description('Azure AD tenant ID.')
param tenantId string

@description('Bicep-derived secrets to create (storage connection string, Cosmos key). Externally owned secrets (kafka-username, kafka-password, resend-api-key) are NOT managed here — deploy.yml writes them, so a Bicep apply never overwrites them.')
@secure()
param secrets object

var secretNames = [
  'AzureWebJobsStorage'
  'azure-storage-connection-string'
  'azure-cosmos-key'
]

// NOTE: this module intentionally does NOT accept the Function App's managed identity principal
// id as a parameter. Doing so would create a circular module dependency, because the Function
// App module needs this vault's URI to build its Key Vault reference app settings. The
// "Key Vault Secrets User" role assignment is created in the root main.bicep instead, after both
// this module and the function-app module have been deployed.
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  properties: {
    tenantId: tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
  }
}

resource secretResources 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = [for secretName in secretNames: {
  parent: keyVault
  name: secretName
  properties: {
    value: secrets[secretName]
  }
}]

output vaultUri string = keyVault.properties.vaultUri
output vaultName string = keyVault.name
output vaultId string = keyVault.id
