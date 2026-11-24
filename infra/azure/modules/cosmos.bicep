@description('Name of the Cosmos DB account (globally unique).')
param accountName string

@description('Azure region.')
param location string

@description('SQL database name.')
param databaseName string

@description('Container name for current job postings, partitioned by /companyId.')
param postingsContainerName string

@description('Container name for daily job observations, partitioned by /companyId.')
param observationsContainerName string

@description('Container name for monthly metrics, partitioned by /month.')
param metricsContainerName string

@description('''
Shared database-level throughput (RU/s), applied once and split across all 3 containers (none of
them set their own dedicated throughput, which is required for database-shared throughput to
apply). The default 1000 RU/s matches the free-tier throughput allowance when the account is
eligible. Monitor actual workload and throttling before changing it; additional throughput,
storage, and other features may incur charges. See infra/azure/README.md for cost considerations.
''')
param sharedThroughputRuPerSecond int = 1000

// NOTE (capacity mode is a one-time, unchangeable decision): Cosmos DB accounts cannot be
// converted between serverless and provisioned throughput after creation — switching later
// requires creating a new account and migrating data. This module provisions with
// `enableFreeTier: true` and shared provisioned throughput, not serverless.
resource cosmosAccount 'Microsoft.DocumentDB/databaseAccounts@2024-05-15' = {
  name: accountName
  location: location
  kind: 'GlobalDocumentDB'
  properties: {
    databaseAccountOfferType: 'Standard'
    enableFreeTier: true
    consistencyPolicy: {
      defaultConsistencyLevel: 'Session'
    }
    locations: [
      {
        locationName: location
        failoverPriority: 0
      }
    ]
  }
}

// Shared (database-level) manual throughput — NOT autoscale. Containers below intentionally omit
// their own `throughput`/
// `autoscaleSettings` so they draw from this shared pool instead of requiring their own minimum
// (a dedicated per-container minimum of 400 RU/s x 3 containers would exceed the free 1000 RU/s).
resource sqlDatabase 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases@2024-05-15' = {
  parent: cosmosAccount
  name: databaseName
  properties: {
    resource: {
      id: databaseName
    }
    options: {
      throughput: sharedThroughputRuPerSecond
    }
  }
}

resource postingsContainer 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2024-05-15' = {
  parent: sqlDatabase
  name: postingsContainerName
  properties: {
    resource: {
      id: postingsContainerName
      partitionKey: {
        paths: ['/companyId']
        kind: 'Hash'
      }
    }
  }
}

resource observationsContainer 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2024-05-15' = {
  parent: sqlDatabase
  name: observationsContainerName
  properties: {
    resource: {
      id: observationsContainerName
      partitionKey: {
        paths: ['/companyId']
        kind: 'Hash'
      }
    }
  }
}

resource metricsContainer 'Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers@2024-05-15' = {
  parent: sqlDatabase
  name: metricsContainerName
  properties: {
    resource: {
      id: metricsContainerName
      partitionKey: {
        paths: ['/month']
        kind: 'Hash'
      }
    }
  }
}

output endpoint string = cosmosAccount.properties.documentEndpoint

@secure()
output primaryKey string = cosmosAccount.listKeys().primaryMasterKey
