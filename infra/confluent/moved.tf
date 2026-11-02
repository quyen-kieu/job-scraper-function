# One-time state migration for module.acls.
#
# ACLs used to be keyed by list index ("0".."13"), so inserting or removing a single grant shifted
# every later index and forced Terraform to destroy/recreate unrelated ACLs (and could race:
# creating an identical ACL under a new index and then deleting it under the old one). They are
# now keyed by "role:resource_type:resource_name:operation". These blocks re-address the existing
# ACLs in state without any Confluent API call.
#
# Old index 8 (persistence WRITE notifications.daily) is intentionally NOT moved: that grant moved
# to the normalizer, so Terraform destroys it and creates the normalizer's grant.
#
# Safe to delete once applied to every environment's state (dev and prod).

moved {
  from = module.acls.confluent_kafka_acl.this["0"]
  to   = module.acls.confluent_kafka_acl.this["scraper:TOPIC:job.scrape.commands:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["1"]
  to   = module.acls.confluent_kafka_acl.this["scraper:GROUP:job-scraper-scraper-consumer-group:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["2"]
  to   = module.acls.confluent_kafka_acl.this["scraper:TOPIC:job.scrape.completed:WRITE"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["3"]
  to   = module.acls.confluent_kafka_acl.this["normalizer:TOPIC:job.scrape.completed:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["4"]
  to   = module.acls.confluent_kafka_acl.this["normalizer:GROUP:job-scraper-normalizer-consumer-group:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["5"]
  to   = module.acls.confluent_kafka_acl.this["normalizer:TOPIC:job.postings.normalized:WRITE"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["6"]
  to   = module.acls.confluent_kafka_acl.this["persistence:TOPIC:job.postings.normalized:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["7"]
  to   = module.acls.confluent_kafka_acl.this["persistence:GROUP:job-scraper-persistence-consumer-group:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["9"]
  to   = module.acls.confluent_kafka_acl.this["metrics:TOPIC:job.postings.normalized:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["10"]
  to   = module.acls.confluent_kafka_acl.this["metrics:GROUP:job-scraper-metrics-consumer-group:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["11"]
  to   = module.acls.confluent_kafka_acl.this["metrics:TOPIC:job.metrics.monthly:WRITE"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["12"]
  to   = module.acls.confluent_kafka_acl.this["email:TOPIC:notifications.daily:READ"]
}
moved {
  from = module.acls.confluent_kafka_acl.this["13"]
  to   = module.acls.confluent_kafka_acl.this["email:GROUP:job-scraper-email-consumer-group:READ"]
}

