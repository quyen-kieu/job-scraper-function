# Kafka Local Setup

Set Kafka credentials as environment variables and keep them out of source control.
The Azure Functions Kafka triggers use SASL/PLAIN over TLS; changing the
producer's protocol/mechanism settings does not change those trigger annotations.
The snippets below use placeholders and do not provision a broker or topics.

## PowerShell (current session)

```powershell
$env:KAFKA_ENABLED = "true"
$env:KAFKA_BOOTSTRAP_SERVERS = "pkc-xxxxx.region.provider.confluent.cloud:9092"
$env:KAFKA_SECURITY_PROTOCOL = "SASL_SSL"
$env:KAFKA_SASL_MECHANISM = "PLAIN"
$env:KAFKA_SCRAPE_COMMANDS_TOPIC = "job.scrape.commands"
$env:KAFKA_USERNAME = "<api-key>"
$env:KAFKA_PASSWORD = "<api-secret>"
```

For local Functions, the ignored `local.settings.json` is another way to set
these values along with the remaining topic and consumer-group settings. Avoid
persisting credentials in user-level environment variables or shell history.

## Infrastructure as code

The [architecture reference](docs/architecture.md#kafka-contracts) describes the default
topics and payloads. Terraform in `infra/confluent/` manages five primary topics and five
dead-letter topics against an existing cluster; it does not create a cluster.

If the topics already exist, the [Confluent runbook](infra/confluent/README.md) describes
the import and plan checks needed before applying Terraform.
