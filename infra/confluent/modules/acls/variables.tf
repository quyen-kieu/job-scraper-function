variable "kafka_cluster_id" {
  description = "ID of the existing shared Kafka cluster the ACLs are scoped to."
  type        = string
}

variable "kafka_rest_endpoint" {
  description = "REST endpoint of the Kafka cluster, used by the Confluent provider for ACL operations."
  type        = string
}

variable "kafka_api_key" {
  description = "Cluster-scoped API key with ACL administration rights, used only to manage ACLs via Terraform."
  type        = string
  sensitive   = true
}

variable "kafka_api_secret" {
  description = "Secret for kafka_api_key."
  type        = string
  sensitive   = true
}

variable "acls" {
  description = "List of ACL grants to create. principal_id must be a Confluent service account id (e.g. sa-xxxxx)."
  type = list(object({
    principal_id  = string
    resource_type = string # "TOPIC" or "GROUP"
    resource_name = string
    pattern_type  = string # "LITERAL" or "PREFIXED"
    operation     = string # e.g. "READ", "WRITE", "DESCRIBE"
    permission    = string # "ALLOW" or "DENY"
  }))
}

