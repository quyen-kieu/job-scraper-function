# Architecture

## Purpose and boundaries

The application collects public job listings from the configured company career
sites, stores raw responses and normalized data, builds monthly metrics, and
sends per-company email summaries. It runs on Azure Functions with Spring
Boot; Kafka connects the stages. Azure resources and Confluent Cloud are
provisioned separately. See [company sources](company-sources.md) and the
deployment runbooks for [Azure](../infra/azure/README.md) and
[Confluent](../infra/confluent/README.md).

## Data flow

```text
Timer or HTTP trigger
  -> job.scrape.commands
  -> scraper -> raw Blob Storage (or local files)
  -> job.scrape.completed
  -> normalizer -> job.postings.normalized -> Cosmos postings + observations
                \-> notifications.daily -> email
  -> normalized postings -> monthly metrics -> Cosmos + job.metrics.monthly
```

The daily timer publishes a command for each enabled company using a shared
run ID. The HTTP trigger can publish a command for one company or each enabled
company. A successful trigger response is not evidence that the broker has
acknowledged every command or that the scrape, storage, or email succeeded.

The scraper fetches a capped number of search pages and attempts details for
every discovered job when required by the source. It writes raw responses and
plain-text descriptions to storage, then publishes a completion event containing
job metadata, description references, and any collected errors. The normalizer classifies each job
against the current posting, publishes normalized postings, and then publishes
one notification event for that company. The email lists scraped jobs and
includes available salary information and failures. The persistence consumer
reads the referenced description from storage before upserting the posting.
Full descriptions are not placed in Kafka messages.

The persistence and metrics functions independently consume normalized
postings. **The notification does not wait for persistence to finish**, and
publishing normalized postings is asynchronous. A sent email is therefore not
proof that all corresponding documents have been saved. Conversely, partial
page failures can be included in an otherwise completed notification.

## Kafka contracts

Topic names are configurable; these are the defaults. Producers use Jackson
JSON and consumers deserialize the corresponding Java records. The record
definitions, rather than a generic event envelope, describe the current
payloads.

| Topic | Key | Value | Purpose |
| --- | --- | --- | --- |
| `job.scrape.commands` | Company ID | [`ScrapeCompanyCommand`](../src/main/java/com/jobscraper/application/command/ScrapeCompanyCommand.java) | Request one company scrape |
| `job.scrape.completed` | Company ID | [`ScrapeCompletedEvent`](../src/main/java/com/jobscraper/application/command/ScrapeCompletedEvent.java) | Results, raw paths, and errors |
| `job.postings.normalized` | Company ID and external job ID | [`NormalizedJobPosting`](../src/main/java/com/jobscraper/domain/job/NormalizedJobPosting.java) | Current normalized posting |
| `notifications.daily` | Run ID | [`DailyNotificationRequestedEvent`](../src/main/java/com/jobscraper/application/notification/DailyNotificationRequestedEvent.java) | One company's email content |
| `job.metrics.monthly` | Company ID and month | [`MonthlyMetricPublishedEvent`](../src/main/java/com/jobscraper/application/metrics/MonthlyMetricPublishedEvent.java) | Updated monthly metric |

Dead-letter topics are provisioned for future use; the current scrapers do not
implement a dedicated retry/backoff and dead-letter production flow.
Kafka-triggered processing failures propagate
to the trigger for redelivery, but page failures collected by scrapers are
reported in the result instead. The scraper consumer logs and skips empty,
malformed, or missing-field commands rather than sending them to a dead-letter
topic. Producers have different acknowledgment
behavior; do not assume every publication has been broker-acknowledged merely
because a handler returned.

## Stored data

- **Raw storage:** search responses and fetched detail responses are written to
  Azure Blob Storage when enabled, or to local files. Blob paths follow
  `raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/search-page-{n}.json` and
  `raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/job-{jobId}.html`; run summaries
  use `normalized/{companyId}/{yyyy}/{MM}/{dd}/{runId}/run-summary.json`.
  Local file mode places all three under the configured root followed by
  `{companyId}/{yyyy}/{MM}/{dd}/{runId}`. The date comes from the host's local
  clock. Detail files use the `.html` suffix even if the source response is JSON.
  Readable descriptions are also stored separately as
  `raw/{companyId}/{yyyy}/{MM}/{dd}/{runId}/description-{jobIdHash}.txt`
  (under the equivalent local run directory in file mode). Raw files may contain
  full descriptions and other source-site fields. Frost Bank stores the extracted
  search-data JSON rather than the complete search-page HTML.
- **Cosmos DB:** `job-postings` contains current normalized postings, keyed by
  company and external job ID. Fields include title, normalized title, level,
  location, posted date, URL, timestamps, activity state, content hash, and
  `description` as plain text when it was successfully obtained. It does not
  store salary as a separate field. Failed or missing descriptions do not
  overwrite previously stored text. Description-only changes do not affect
  the content hash or new/changed notification counts.
  `job-observations` stores one upserted observation per company, posting and
  UTC **persistence** date; delayed processing can differ from the scrape date.
  `monthly-metrics` contains aggregates by company, month,
  normalized title, and level.
- **Email:** the notification includes the postings seen in that company's run.
  Salary, if available, is supplied to the notification rather than stored in
  the normalized posting.

Postings and observations use `/companyId` as their Cosmos partition key;
monthly metrics use `/month`. Observation `runId` identifies the persistence
invocation, not the originating scrape run, because normalized posting events
do not carry the original run ID. Data access steps are in
[scrape and verify](runbooks/scrape-and-verify.md).

The normalizer substitutes the current UTC date if a source posting date is
missing. The live metrics consumer buckets postings by the UTC month of
`firstSeenAt`, not by posting date or each later observation date. The internal
monthly rebuild service instead reads persisted observations for their recorded
dates; it has no HTTP or timer trigger and does not delete old metric documents.

## Important limitations

- Search-page caps, detail failures, and source changes can make results
  incomplete. Fetching all discovered details increases runtime and requests;
  very large messages or descriptions can also exceed broker or Cosmos limits.
  Details and ordering differ by source; see [company sources](company-sources.md).
- `JOB_SCRAPER_MAX_RETRIES` and the global `JOB_SCRAPER_ENABLED` are present in
  configuration, but the current scrape/trigger code does not use them to retry
  HTTP requests or disable runs. Disable companies individually instead. Kafka
  producer retries are a separate producer setting.
- New/changed classification reads current storage before publication. Concurrent
  runs or delayed persistence can affect counts. Inactive detection is not yet
  implemented, so the notification's inactive count is zero.
- Notification deduplication uses an in-memory `runId:companyId` guard, not a
  durable or distributed guarantee. Restarts, multiple instances, and failures
  can still result in duplicate email.
- With Cosmos disabled, no-op repositories do not persist data and every posting
  is classified as new. With Resend disabled, the notification sender logs the
  summary instead of sending email. If neither Blob nor file storage is enabled,
  raw response calls return mock paths without writing files and description
  storage reports errors. The scraper and persistence consumer must access the
  same Blob container or, for local file mode, a shared filesystem. Blob data
  must remain available until persistence and any redeliveries complete.
- HTTP credentials, cloud resource names, and email configuration belong in
  local ignored settings or deployment secret stores, never in documentation or
  source control. See [local development](runbooks/local-development.md) and
  [Azure deployment](../infra/azure/README.md).
