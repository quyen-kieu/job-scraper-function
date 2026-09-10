output "topic_names" {
  description = "Names of all managed topics (primary + DLT)."
  value       = [for t in confluent_kafka_topic.this : t.topic_name]
}

output "topic_ids" {
  description = "Map of topic name -> Confluent-assigned topic resource id, useful for terraform import statements."
  value       = { for name, t in confluent_kafka_topic.this : name => t.id }
}

