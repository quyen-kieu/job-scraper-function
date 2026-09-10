variable "confluent_cloud_api_key" {
  description = "Org-level Confluent Cloud API key used only by Terraform to authenticate to the Confluent provider. Supplied via TF_VAR_confluent_cloud_api_key, never committed."
  type        = string
  sensitive   = true
}

variable "confluent_cloud_api_secret" {
  description = "Secret for confluent_cloud_api_key. Supplied via TF_VAR_confluent_cloud_api_secret, never committed."
  type        = string
  sensitive   = true
}

variable "confluent_environment_id" {
  description = "ID of the existing Confluent environment that owns shared-kafka-dev-us-central1."
  type        = string
}

variable "confluent_cluster_id" {
  description = "ID of the existing shared-kafka-dev-us-central1 cluster. Looked up as a data source, never created by Terraform."
  type        = string
}

