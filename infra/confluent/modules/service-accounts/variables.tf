variable "service_accounts" {
  description = "Map of logical role name (scraper, normalizer, persistence, metrics, email) -> display name/description."
  type = map(object({
    display_name = string
    description   = string
  }))
}

variable "environment_id" {
  description = "ID of the Confluent environment that owns the cluster the API keys are scoped to."
  type        = string
}

variable "kafka_cluster_id" {
  description = "ID of the existing shared Kafka cluster the per-role API keys are scoped to."
  type        = string
}

variable "kafka_cluster_api_version" {
  description = "api_version of the Kafka cluster resource, required by confluent_api_key's managed_resource block."
  type        = string
}

variable "kafka_cluster_kind" {
  description = "kind of the Kafka cluster resource, required by confluent_api_key's managed_resource block."
  type        = string
  default     = "Cluster"
}

