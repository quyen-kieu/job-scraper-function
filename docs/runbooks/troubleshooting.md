# Troubleshooting

Start with the returned run ID and company ID, then follow the stages in
[scrape and verify](scrape-and-verify.md). Keep keys, account identifiers,
email addresses, and private payloads out of shared logs and reports.

| Symptom | Checks |
| --- | --- |
| Only McKesson emailed after deployment | The deployment smoke test triggers McKesson by default. Trigger the other enabled companies separately or omit `companyId` to trigger all enabled companies; confirm the timer's enabled-company settings. |
| Trigger responds but no scrape appears | Confirm Kafka is enabled and reachable, credentials and topic names match, the scraper trigger is running, and the company ID has a registered scraper. A successful HTTP response does not guarantee broker acknowledgment or completion. |
| Scrape has few jobs or reported errors | Inspect search/detail page counts and errors, compare the source site, and check page/detail caps and request delay. Source changes, throttling, and transient HTTP failures can prevent complete coverage. Frost's source order is not guaranteed newest first. |
| McKesson results include other job titles | Its implemented search sends an empty keyword and does not filter specifically for Software Engineer. |
| Email missing | Check the normalizer and notification consumer logs, the `RESEND_ENABLED` setting, provider configuration, sender verification, provider delivery status, and inbox filtering. Redact sensitive settings. |
| Email arrived but Cosmos documents are missing | Notification and persistence are independent downstream paths. Check the persistence consumer, Cosmos settings, container names and partition keys, and errors; wait for processing before querying again. |
| Description or salary missing from Cosmos | Expected for normalized postings. Inspect the raw search or fetched detail response in Blob Storage or local file storage; not all detail pages are fetched. |
| Duplicate email or unexpected counts | The notification guard is in-memory and counts are based on the stored state at normalization time. Restarts, concurrent instances/runs, or delayed persistence can affect results. |
| Local Functions host fails to start | Check Java, Maven, Functions Core Tools, `AzureWebJobsStorage`, the broker and enabled integrations. The sample settings contain placeholders and must be configured locally. |
| Disabling the global scraper setting has no effect | `JOB_SCRAPER_ENABLED` is currently not checked by the trigger or producer. Disable each company using its `JOB_SCRAPER_COMPANIES_<n>_ENABLED` setting. |

Scrape errors can be captured in a completed result, while thrown processing
errors propagate to the Kafka trigger. Dedicated application retry/backoff and
dead-letter publishing are not implemented. See
[architecture](../architecture.md#important-limitations) for other limits.
