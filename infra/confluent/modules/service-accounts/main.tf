terraform {
  required_providers {
    confluent = {
      source = "confluentinc/confluent"
    }
  }
}

# One service account per logical pipeline stage. NOTE (Stage 7 scope decision, see README):
# the running Function App today authenticates with a single shared KAFKA_USERNAME/KAFKA_PASSWORD
# credential (KafkaProperties has no per-listener override). These accounts and their API keys are
# provisioned as groundwork for a future code change that adopts per-consumer-group credentials;
# they are not yet consumed by the deployed application.
resource "confluent_service_account" "this" {
  for_each     = var.service_accounts
  display_name = each.value.display_name
  description  = each.value.description
}

resource "confluent_api_key" "this" {
  for_each     = var.service_accounts
  display_name = "${each.value.display_name}-kafka-api-key"
  description  = "Kafka API key for ${each.value.display_name}"

  owner {
    id          = confluent_service_account.this[each.key].id
    api_version = confluent_service_account.this[each.key].api_version
    kind        = confluent_service_account.this[each.key].kind
  }

  managed_resource {
    id          = var.kafka_cluster_id
    api_version = var.kafka_cluster_api_version
    kind        = var.kafka_cluster_kind

    environment {
      id = var.environment_id
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}
