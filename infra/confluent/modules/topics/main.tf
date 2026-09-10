terraform {
  required_providers {
    confluent = {
      source = "confluentinc/confluent"
    }
  }
}

# NOTE: every topic managed here already exists in the shared cluster and was created manually.
# The first `terraform apply` in a fresh state MUST be preceded by `terraform import` for each
# key in var.topics (see infra/confluent/README.md), otherwise the provider will fail with a
# "topic already exists" error instead of adopting the existing resource.
resource "confluent_kafka_topic" "this" {
  for_each = var.topics

  kafka_cluster {
    id = var.kafka_cluster_id
  }
  topic_name       = each.key
  partitions_count = each.value.partitions_count
  rest_endpoint    = var.kafka_rest_endpoint
  config           = each.value.config

  credentials {
    key    = var.kafka_api_key
    secret = var.kafka_api_secret
  }

  lifecycle {
    prevent_destroy = true
  }
}

