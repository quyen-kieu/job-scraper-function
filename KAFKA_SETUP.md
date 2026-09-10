# Kafka Local Setup

Set Kafka credentials as environment variables and keep them out of source control.

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

## PowerShell (persist for current user)

```powershell
[Environment]::SetEnvironmentVariable("KAFKA_ENABLED","true","User")
[Environment]::SetEnvironmentVariable("KAFKA_BOOTSTRAP_SERVERS","pkc-xxxxx.region.provider.confluent.cloud:9092","User")
[Environment]::SetEnvironmentVariable("KAFKA_SECURITY_PROTOCOL","SASL_SSL","User")
[Environment]::SetEnvironmentVariable("KAFKA_SASL_MECHANISM","PLAIN","User")
[Environment]::SetEnvironmentVariable("KAFKA_SCRAPE_COMMANDS_TOPIC","job.scrape.commands","User")
[Environment]::SetEnvironmentVariable("KAFKA_USERNAME","<api-key>","User")
[Environment]::SetEnvironmentVariable("KAFKA_PASSWORD","<api-secret>","User")
```

Open a new terminal after user-level changes.

## Infrastructure as code (Stage 7)

The topic settings documented here (and in the root README's "Confluent Kafka topic reference"
section) are now also expressed as reviewable Terraform in `infra/confluent/`. That module
manages the same 5 primary topics + 5 DLTs against the **existing** `shared-kafka-dev-us-central1`
cluster (created manually, as described above) — it does not create a new cluster.

Because the topics already exist, `infra/confluent/README.md` documents a required one-time
`terraform import` runbook that must run before the first `terraform apply`; skipping it will
cause Terraform to fail with a "topic already exists" error. See that file for the exact commands.


