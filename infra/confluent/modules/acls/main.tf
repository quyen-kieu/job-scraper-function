terraform {
  required_providers {
    confluent = {
      source = "confluentinc/confluent"
    }
  }
}

resource "confluent_kafka_acl" "this" {
  # Keyed by content (not list index) so adding/removing one grant never shifts the address of,
  # and forces replacement of, unrelated ACLs.
  for_each = { for acl in var.acls : "${acl.role}:${acl.resource_type}:${acl.resource_name}:${acl.operation}" => acl }

  kafka_cluster {
    id = var.kafka_cluster_id
  }
  resource_type = each.value.resource_type
  resource_name = each.value.resource_name
  pattern_type  = each.value.pattern_type
  principal     = "User:${each.value.principal_id}"
  host          = "*"
  operation     = each.value.operation
  permission    = each.value.permission
  rest_endpoint = var.kafka_rest_endpoint

  credentials {
    key    = var.kafka_api_key
    secret = var.kafka_api_secret
  }
}
