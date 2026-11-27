# Architecture

## Purpose and boundaries

The application collects public job listings from the configured company career
sites, stores normalized data and descriptions, builds monthly metrics, and
sends per-company email summaries. It runs on Azure Functions with Spring
Boot; Kafka connects the stages. Azure resources and Confluent Cloud are
provisioned separately. See [company sources](company-sources.md) and the
deployment runbooks for [Azure](../infra/azure/README.md) and
[Confluent](../infra/confluent/README.md).

## Data flow

```text
Timer or HTTP trigger
  -> job.scrape.commands
  -> scraper -> temporary Cosmos description handoffs (or local files)
  -> job.scrape.completed
  -> normalizer -> Cosmos postings + observations -> job.postings.normalized
                \-> weekly reconciliation -> notifications.daily -> email
  -> normalized postings -> monthly metrics -> Cosmos + job.metrics.monthly
```

The daily timer publishes a command for each enabled company using a shared
run ID. The HTTP trigger can publish a command for one company or each enabled
company. A successful trigger response is not evidence that the broker has
acknowledged every command or that the scrape, storage, or email succeeded.

The scraper scans all search pages by default. For a company without a successful
full reconciliation, and again seven UTC calendar days after the previous one,
it attempts all details. Between these runs it fetches details only for new,
reactivated, metadata-changed, or description-missing jobs; First Citizens embeds
descriptions in search pages. It writes source descriptions to temporary Cosmos
handoffs and publishes a completion event containing metadata, references and
errors. The normalizer classifies each job and synchronously persists its posting
and observation before publishing normalized messages. For a successful complete
full refresh it marks previously active, unseen jobs inactive, then publishes the
notification. Postings and existing observations are never deleted.

The persistence consumer remains for older normalized messages. New normalized
messages carry a marker to avoid a second write. Metrics still consume normalized
messages independently. Production does not archive raw search or detail responses;
full descriptions are not placed in Kafka messages.

Completion events include `searchComplete`, which is true only when pagination
reaches a stable reported total and the number of unique job IDs matches it.
Older events default to false. This flag covers search enumeration only: detail
or storage errors are reported separately, and changing source results can still
produce a misleadingly consistent count. Reconciliation requires this flag,
a full refresh, no recorded errors, and a nonempty result. Empty full results
are treated as suspicious and do not advance the weekly schedule.

Kafka acknowledgement does not imply persistence: the normalizer instead writes
each posting and observation before reconciliation. Concurrent writes and
reconciliations use a transactional, ETag-guarded control document in each
company partition to prevent stale runs from reactivating missing jobs. A failed
or capped run does not reconcile. Partial page and detail failures can still be
included in a notification. A notification is not proof of inbox delivery or of
an error-free complete scan.

## Kafka contracts

Topic names are configurable; these are the defaults. Producers use Jackson
JSON and consumers deserialize the corresponding Java records. The record
definitions, rather than a generic event envelope, describe the current
payloads.

| Topic | Key | Value | Purpose |
| --- | --- | --- | --- |
| `job.scrape.commands` | Company ID | [`ScrapeCompanyCommand`](../src/main/java/com/jobscraper/application/command/ScrapeCompanyCommand.java) | Request one company scrape |
| `job.scrape.completed` | Company ID | [`ScrapeCompletedEvent`](../src/main/java/com/jobscraper/application/command/ScrapeCompletedEvent.java) | Results, handoff references, and errors |
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

- **Handoffs:** `description-handoffs` stores source descriptions, keyed by
  run, job and content hash and partitioned by `/companyId`. Its 30-day TTL
  expires each document after its last modification. Replaying a reference after
  expiry cannot recover that description. The production scraper does not
  retain raw responses; its `not-stored://` markers are not retrievable files.
  Local file mode still writes search responses, detail responses, summaries and
  descriptions beneath the configured root, company, host-local date and run.
- **Cosmos DB:** `job-postings` contains current normalized postings, keyed by
  company and external job ID. Fields include title, normalized title, level,
  location, posted date, URL, timestamps, activity state, content hash, and
  `descriptionHtml` with source HTML when available, and `descriptionPlainText`
  with readable text. `salaryRange` is stored when found. New upserts omit the legacy `description` field; an older
  document containing only `description` is converted to plain text when it is
  next upserted. Documents not revisited still retain the legacy field.
  Failed or missing descriptions do not overwrite previously
  stored values; a successful plain-text-only scrape clears prior HTML.
  HTML is untrusted and must be sanitized before rendering. Description-only changes do not affect the content hash
  or new/changed notification counts.
  `job-postings` also contains one `recordType: "scrape-control"` document per
  company, with ID `__scrape_control__`. Queries and viewers must exclude it,
  for example with `IS_DEFINED(c.externalJobId)`. It shares the postings
  partition because Cosmos transactions cannot span containers.
  `job-observations` stores one upserted observation per company, posting and
  UTC scrape date. Legacy events without a run ID use the UTC persistence date.
  `monthly-metrics` contains aggregates by company, month,
  normalized title, and level.
- **Email:** the notification includes the postings seen in that company's run
  and the available salary from the current or most recently stored detail.

Azure Functions still requires its own Storage account and deployment Blob
container. The Confluent Terraform workflow separately uses an Azure Blob
backend for state. Neither is used to store scraper descriptions.

Postings and observations use `/companyId` as their Cosmos partition key;
monthly metrics use `/month`. New observation `runId` values identify the
originating scrape; legacy events without a run ID use a persistence invocation
ID. Data access steps are in
[scrape and verify](runbooks/scrape-and-verify.md).

The normalizer substitutes the current UTC date if a source posting date is
missing. The live metrics consumer buckets postings by the UTC month of
`firstSeenAt`, not by posting date or each later observation date. The internal
monthly rebuild service instead reads persisted observations for their recorded
dates; it has no HTTP or timer trigger and does not delete old metric documents.

## Important limitations

- Search-page caps, detail failures, and source changes can make results
  incomplete. Fetching all discovered details increases runtime and requests;
  very large run-completion or notification messages or descriptions can also
  exceed broker or Cosmos limits. Oversized completion events fail before
  reconciliation; a notification can fail after reconciliation. The current
  pipeline does not chunk run messages. The producer's request size is configured
  by `KAFKA_MAX_REQUEST_SIZE` (default 1,048,576 bytes); the broker may impose
  a lower limit.
  Details and ordering differ by source; see [company sources](company-sources.md).
- `JOB_SCRAPER_MAX_RETRIES` and the global `JOB_SCRAPER_ENABLED` are present in
  configuration, but the current scrape/trigger code does not use them to retry
  HTTP requests or disable runs. Disable companies individually instead. Kafka
  producer retries are a separate producer setting.
- New/changed classification reads current storage before persistence; concurrent
  runs can affect counts. Inactive means absent from the configured filtered
  search, not necessarily closed by the employer. Offset pagination is not a
  source-side snapshot, even when counts appear consistent. Workday relative
  dates and description-only changes are not daily change indicators; weekly
  full detail fetches discover description changes.
- Notification deduplication uses an in-memory `runId:companyId` guard, not a
  durable or distributed guarantee. Restarts, multiple instances, and failures
  can still result in duplicate email.
- With Cosmos disabled, no-op repositories do not persist data and every posting
  is classified as new. With Resend disabled, the notification sender logs the
  summary instead of sending email. If neither Cosmos handoff nor file mode is enabled,
  raw response calls return mock paths without writing files and description
  storage reports errors. The scraper and normalizer must access the
  same Cosmos handoff container or, for local file mode, a shared filesystem.
  Before switching from the old Blob-backed version, drain outstanding messages
  that reference Blob files; the new version cannot read those references.
- HTTP credentials, cloud resource names, and email configuration belong in
  local ignored settings or deployment secret stores, never in documentation or
  source control. See [local development](runbooks/local-development.md) and
  [Azure deployment](../infra/azure/README.md).
