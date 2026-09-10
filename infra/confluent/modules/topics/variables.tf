variable "kafka_cluster_id" {
  description = "ID of the existing shared Confluent Kafka cluster (e.g. shared-kafka-dev-us-central1)."
  type        = string
}

variable "kafka_rest_endpoint" {
  description = "REST endpoint of the Kafka cluster, used by the Confluent provider for topic/ACL operations."
  type        = string
}

variable "environment_id" {
  description = "ID of the existing Confluent environment that owns the cluster."
  type        = string
}

variable "kafka_api_key" {
  description = "Cluster-scoped API key with topic administration rights, used only to manage topics via Terraform."
  type        = string
  sensitive   = true
}

variable "kafka_api_secret" {
  description = "Secret for kafka_api_key."
  type        = string
  sensitive   = true
}

variable "topics" {
  description = <<-EOT
    Map of topic name -> topic configuration. All 5 primary topics and their 5 DLTs are expected here.
    IMPORTANT: every one of these topics already exists in the shared cluster (created manually) —
    see infra/confluent/README.md for the required one-time `terraform import` runbook before the
    first `terraform apply`.
  EOT
  type = map(object({
    partitions_count = number
    config           = map(string)
  }))
}

