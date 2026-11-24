# Job Scraper

A Java and Spring Boot application that collects public job listings, saves
normalized data and descriptions in Cosmos DB, calculates monthly metrics, and
emails a summary for each company. Azure Functions runs the pipeline, Kafka
connects its stages, and Resend provides notifications. Functions still requires
its own storage account; scraper data is not archived in Blob Storage.

My main objectives for this project: to learn the technologies and to do so
using free tier cloud services. These pillars drive the design decisions and
project outcomes.

The configured sources are McKesson, GM Financial, First Citizens Bank, and
Frost Bank. Searches are bounded by a configurable search-page limit, so
results are not necessarily exhaustive. See [company sources](docs/company-sources.md)
for the searches, source-specific behavior, and implementations.

## Get started

Install Java 21, Maven, and Azure Functions Core Tools v4. Copy the sample
settings, configure your own credentials and services in the ignored local
file, then build and run:

```powershell
Copy-Item local.settings.sample.json local.settings.json
mvn clean package
mvn azure-functions:run
```

The sample uses local files for raw output and disables Cosmos DB and email; a
running Kafka broker is still needed for the full pipeline. See
the [local development runbook](docs/runbooks/local-development.md) before
running. Do not commit credentials, private settings, or scrape output.

## Documentation

| Document | Use it for |
| --- | --- |
| [Architecture](docs/architecture.md) | Data flow, Kafka contracts, storage, and current limitations |
| [Company sources](docs/company-sources.md) | Supported searches, page limits, and source-specific notes |
| [Local development](docs/runbooks/local-development.md) | Prerequisites, configuration, and local execution |
| [Scrape and verify](docs/runbooks/scrape-and-verify.md) | Manual triggers, email checks, and reading Cosmos DB and raw files |
| [Troubleshooting](docs/runbooks/troubleshooting.md) | Diagnosing missing results, emails, and data |
| [Kafka setup](KAFKA_SETUP.md) | Local Kafka configuration |
| [Azure infrastructure](infra/azure/README.md) | Bicep deployment, secrets, and cost considerations |
| [Confluent infrastructure](infra/confluent/README.md) | Terraform setup, imports, and operational safeguards |
| [GitHub Actions](.github/workflows/README.md) | CI/CD configuration and secret scanning |

Manual triggers enqueue work; they do not prove that a scrape, persistence, or
email completed. The deployment smoke test triggers McKesson by default, not
every company. Check each downstream stage as described in the verification
runbook.
