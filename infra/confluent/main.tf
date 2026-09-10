terraform {
  required_version = ">= 1.7.0"

  required_providers {
    confluent = {
      source  = "confluentinc/confluent"
      version = "~> 2.6"
    }
  }

  # Remote state: Azure Storage Account backend (see backend.tf for the empty partial config and
  # docs/STAGE7_PLAN.md open question #2 for the reused-vs-dedicated storage account decision).
  # Concrete backend values (resource_group_name, storage_account_name, container_name, key) are
  # supplied at `terraform init -backend-config=...` time per environment, never hardcoded here.
  backend "azurerm" {}
}

provider "confluent" {
  cloud_api_key    = var.confluent_cloud_api_key
  cloud_api_secret = var.confluent_cloud_api_secret
}

# The cluster and its environment already exist (created manually, see README's "Confluent Kafka
# topic reference" section) — these are read-only lookups, never managed resources.
data "confluent_environment" "this" {
  id = var.confluent_environment_id
}

data "confluent_kafka_cluster" "shared" {
  id = var.confluent_cluster_id
  environment {
    id = data.confluent_environment.this.id
  }
}

locals {
  # Matches the README's "Topic creation settings (all topics)" baseline exactly.
  topic_baseline_config = {
    "cleanup.policy"    = "delete"
    "retention.ms"      = "604800000"
    "retention.bytes"   = "-1"
    "max.message.bytes" = "1048576"
  }

  # Matches the README's "Confluent Kafka topic reference" primary + DLT tables exactly.
  primary_topics = [
    "job.scrape.commands",
    "job.scrape.completed",
    "job.postings.normalized",
    "job.metrics.monthly",
    "notifications.daily",
  ]

  all_topics = merge(
    { for t in local.primary_topics : t => {
      partitions_count = 1
      config           = local.topic_baseline_config
    } },
    { for t in local.primary_topics : "${t}.DLT" => {
      partitions_count = 1
      config           = local.topic_baseline_config
    } }
  )

  # Five logical roles matching the five Kafka consumer groups (KAFKA_*_CONSUMER_GROUP settings).
  # Stage 7 scope decision: WRITE on notifications.daily is granted to `persistence`, because
  # NormalizedPostingPersistenceHandler (not the email consumer) is the actual publisher of
  # DailyNotificationRequestedEvent via RunCompletionAggregator (see docs/STAGE7_PLAN.md §0/§5).
  service_accounts = {
    scraper = {
      display_name = "sa-job-scraper-scraper"
      description  = "Scraper consumer (job.scrape.commands) / producer (job.scrape.completed)"
    }
    normalizer = {
      display_name = "sa-job-scraper-normalizer"
      description  = "Normalizer consumer (job.scrape.completed) / producer (job.postings.normalized)"
    }
    persistence = {
      display_name = "sa-job-scraper-persistence"
      description  = "Persistence consumer (job.postings.normalized) / producer (notifications.daily)"
    }
    metrics = {
      display_name = "sa-job-scraper-metrics"
      description  = "Metrics consumer (job.postings.normalized) / producer (job.metrics.monthly)"
    }
    email = {
      display_name = "sa-job-scraper-email"
      description  = "Resend email consumer (notifications.daily)"
    }
  }

  consumer_groups = {
    scraper     = "job-scraper-scraper-consumer-group"
    normalizer  = "job-scraper-normalizer-consumer-group"
    persistence = "job-scraper-persistence-consumer-group"
    metrics     = "job-scraper-metrics-consumer-group"
    email       = "job-scraper-email-consumer-group"
  }

  # Least-privilege ACL grants, one row per (principal, topic-or-group, operation).
  acl_entries = concat(
    # scraper: read commands + its consumer group, write completed
    [
      { principal_id = module.service_accounts.service_account_ids["scraper"], resource_type = "TOPIC", resource_name = "job.scrape.commands", pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["scraper"], resource_type = "GROUP", resource_name = local.consumer_groups["scraper"], pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["scraper"], resource_type = "TOPIC", resource_name = "job.scrape.completed", pattern_type = "LITERAL", operation = "WRITE", permission = "ALLOW" },
    ],
    # normalizer: read completed + its consumer group, write postings.normalized
    [
      { principal_id = module.service_accounts.service_account_ids["normalizer"], resource_type = "TOPIC", resource_name = "job.scrape.completed", pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["normalizer"], resource_type = "GROUP", resource_name = local.consumer_groups["normalizer"], pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["normalizer"], resource_type = "TOPIC", resource_name = "job.postings.normalized", pattern_type = "LITERAL", operation = "WRITE", permission = "ALLOW" },
    ],
    # persistence: read postings.normalized + its consumer group, write notifications.daily
    [
      { principal_id = module.service_accounts.service_account_ids["persistence"], resource_type = "TOPIC", resource_name = "job.postings.normalized", pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["persistence"], resource_type = "GROUP", resource_name = local.consumer_groups["persistence"], pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["persistence"], resource_type = "TOPIC", resource_name = "notifications.daily", pattern_type = "LITERAL", operation = "WRITE", permission = "ALLOW" },
    ],
    # metrics: read postings.normalized + its consumer group, write metrics.monthly
    [
      { principal_id = module.service_accounts.service_account_ids["metrics"], resource_type = "TOPIC", resource_name = "job.postings.normalized", pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["metrics"], resource_type = "GROUP", resource_name = local.consumer_groups["metrics"], pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["metrics"], resource_type = "TOPIC", resource_name = "job.metrics.monthly", pattern_type = "LITERAL", operation = "WRITE", permission = "ALLOW" },
    ],
    # email: read notifications.daily + its consumer group only
    [
      { principal_id = module.service_accounts.service_account_ids["email"], resource_type = "TOPIC", resource_name = "notifications.daily", pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
      { principal_id = module.service_accounts.service_account_ids["email"], resource_type = "GROUP", resource_name = local.consumer_groups["email"], pattern_type = "LITERAL", operation = "READ", permission = "ALLOW" },
    ],
  )
}

module "topics" {
  source = "./modules/topics"

  kafka_cluster_id    = data.confluent_kafka_cluster.shared.id
  kafka_rest_endpoint = data.confluent_kafka_cluster.shared.rest_endpoint
  environment_id      = data.confluent_environment.this.id
  kafka_api_key       = var.confluent_cloud_api_key
  kafka_api_secret    = var.confluent_cloud_api_secret
  topics              = local.all_topics
}

module "service_accounts" {
  source = "./modules/service-accounts"

  service_accounts          = local.service_accounts
  environment_id            = data.confluent_environment.this.id
  kafka_cluster_id          = data.confluent_kafka_cluster.shared.id
  kafka_cluster_api_version = data.confluent_kafka_cluster.shared.api_version
}

module "acls" {
  source = "./modules/acls"

  kafka_cluster_id    = data.confluent_kafka_cluster.shared.id
  kafka_rest_endpoint = data.confluent_kafka_cluster.shared.rest_endpoint
  kafka_api_key       = var.confluent_cloud_api_key
  kafka_api_secret    = var.confluent_cloud_api_secret
  acls                = local.acl_entries
}

