output "topic_names" {
  description = "All managed topic names (5 primary + 5 DLT)."
  value       = module.topics.topic_names
}

output "service_account_ids" {
  description = "Map of logical role name -> Confluent service account id."
  value       = module.service_accounts.service_account_ids
}

output "service_account_api_keys" {
  description = "Map of logical role name -> {key, secret}. Sensitive; not yet consumed by the app (Stage 7 scope decision)."
  value       = module.service_accounts.api_keys
  sensitive   = true
}

