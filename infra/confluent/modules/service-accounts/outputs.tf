output "service_account_ids" {
  description = "Map of logical role name -> Confluent service account resource id (used as the principal in ACL grants)."
  value       = { for role, sa in confluent_service_account.this : role => sa.id }
}

output "api_keys" {
  description = "Map of logical role name -> {key, secret} for each service account's Kafka API key. Sensitive; consumed only by a future Stage 8 secret-sync step."
  value = {
    for role, key in confluent_api_key.this : role => {
      key    = key.id
      secret = key.secret
    }
  }
  sensitive = true
}

