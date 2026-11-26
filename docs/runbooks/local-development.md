# Local development

## Prerequisites

- Java 21, Maven, and Azure Functions Core Tools v4.
- Azurite or another appropriate Azure Storage connection for the Functions
  host. Kafka is needed for the complete event pipeline; Cosmos DB and Resend
  are needed when their integrations are enabled.
- Credentials and resource names must come from your own environment. Never
  commit `local.settings.json`, actual credentials, email addresses, or local
  output. It is an ignored file and can contain sensitive values.

## Build and configure

From the repository root:

```powershell
Copy-Item local.settings.sample.json local.settings.json
mvn clean package
```

Edit the ignored `local.settings.json` locally. Set `AzureWebJobsStorage` for
the Functions host and supply the Kafka bootstrap server and credentials for
your own Kafka cluster. The sample selects local raw storage
(`RAW_STORAGE_MODE=file`) and disables the Cosmos and Resend
integrations. Enable and configure each integration if you need to exercise
it. Do not paste the resulting file into issues, logs, or chats.
The sample sets `JOB_SCRAPER_MAX_SEARCH_PAGES_PER_RUN` to `0`, which removes
the search-page cap for local scrapes. Set a positive number to restore a cap;
negative values are invalid. Existing local settings files must be updated
separately. Azure's Bicep settings retain the three-page limit.
Use `RAW_STORAGE_MODE=cosmos` and enable Cosmos to exercise the cloud handoff
container; it must exist with a 30-day TTL before the scraper runs.
When Cosmos is disabled, no-op repositories discard writes and every posting
is classified as new. When Resend is disabled, notification summaries are logged
rather than emailed.

Kafka triggers and producers require compatible settings and a reachable
broker; the sample's Confluent placeholders are not a working local broker.
Follow [Kafka setup](../../KAFKA_SETUP.md). For cloud infrastructure and
deployment configuration, see [Confluent](../../infra/confluent/README.md)
and [Azure](../../infra/azure/README.md).

## Run and test

```powershell
mvn test
mvn azure-functions:run
```

The local Functions host exposes the HTTP trigger. Instructions for submitting
one or all scrapes and checking the output are in
[scrape and verify](scrape-and-verify.md). Local file storage writes under
`output/raw` by default; it does not persist to Cosmos DB or send email when
those integrations are disabled. `mvn test` excludes tests tagged
`integration` by the Maven Surefire configuration.

The Azure Functions Kafka triggers are configured for SASL/PLAIN over TLS.
Changing `KAFKA_SECURITY_PROTOCOL` or `KAFKA_SASL_MECHANISM` alone configures
the Java producer, not those trigger annotations; a plaintext local broker is
not compatible with the full pipeline without changing the trigger configuration.
The global `JOB_SCRAPER_ENABLED` and `JOB_SCRAPER_MAX_RETRIES` settings are
currently not enforced by the scraper pipeline. Use the per-company enabled
settings to control which companies are triggered.

The direct McKesson scraper runner is useful for source-site checks without
Kafka or the Azure Functions host:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.jobscraper.tools.LocalMcKessonScrapeRunner"
```

This runner writes local raw files and reports counts/errors; it **does not**
exercise Kafka, Cosmos DB, or email. It also runs without a search-page cap.
An uncapped scrape may take longer, generate more source requests, local files
and Kafka events, and may exceed broker message limits. Live sites can change,
rate-limit, or temporarily fail; uncapped paging does not guarantee a stable
or complete snapshot. Use the smallest relevant Maven test selection when
changing a particular scraper, followed by the broader suite as appropriate.
