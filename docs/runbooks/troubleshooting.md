# Troubleshooting

Start with the returned run ID and company ID, then follow the stages in
[scrape and verify](scrape-and-verify.md). Keep keys, account identifiers,
email addresses, and private payloads out of shared logs and reports.

| Symptom | Checks |
| --- | --- |
| Only McKesson emailed after deployment | The deployment smoke test triggers McKesson by default. Trigger the other enabled companies separately or omit `companyId` to trigger all enabled companies; confirm the timer's enabled-company settings. |
| Trigger responds but no scrape appears | Confirm Kafka is enabled and reachable, credentials and topic names match, the scraper trigger is running, and the company ID has a registered scraper. A successful HTTP response does not guarantee broker acknowledgment or completion. |
| Scrape has few jobs or reported errors | Inspect search/detail page counts and errors, compare the source site, and check the search-page cap and request delay. Source changes, throttling, and transient HTTP failures can prevent complete coverage. |
| McKesson results include other job titles | The Workday search filters for Texas, Technology, and United States, not a specific job title. |
| McKesson Workday search fails or returns unexpectedly few jobs | Compare the current site filters and facet IDs with the configured settings in [company sources](../company-sources.md). IDs or site behavior can change; also check the page cap and collected HTTP errors. |
| CVS Workday search fails or returns unexpectedly few jobs | Check the configured job-family and location facet IDs against the current site, and review the page cap and collected HTTP errors. See [company sources](../company-sources.md). |
| Frost Bank, Citi, Fannie Mae, Humana, or Capital One Workday search fails or returns unexpectedly few jobs | Check their configured facet IDs against the current source, and review the page cap and collected HTTP errors. See [company sources](../company-sources.md). |
| Email missing | Check the normalizer and notification consumer logs, the `RESEND_ENABLED` setting, provider configuration, sender verification, provider delivery status, and inbox filtering. Redact sensitive settings. |
| Email arrived but Cosmos documents are missing | Notification and persistence are independent downstream paths. Check the persistence consumer, Cosmos settings, container names and partition keys, and errors; wait for processing before querying again. |
| Description missing from Cosmos | Check scrape errors, the persistence consumer, and access to the Cosmos handoff container or shared local files. A handoff expires 30 days after its last modification; old Blob references cannot be read after cutover. Older records may have only `description`; HTML may be absent when a source provides only text. Jobs outside the search-page cap are not automatically backfilled. A failed detail fetch does not erase existing descriptions. Salary is not stored separately. |
| Duplicate email or unexpected counts | The notification guard is in-memory and counts are based on the stored state at normalization time. Restarts, concurrent instances/runs, or delayed persistence can affect results. |
| Local Functions host fails to start | Check Java, Maven, Functions Core Tools, `AzureWebJobsStorage`, the broker and enabled integrations. The sample settings contain placeholders and must be configured locally. |
| Disabling the global scraper setting has no effect | `JOB_SCRAPER_ENABLED` is currently not checked by the trigger or producer. Disable each company using its `JOB_SCRAPER_COMPANIES_<n>_ENABLED` setting. |

Scrape errors can be captured in a completed result, while thrown processing
errors propagate to the Kafka trigger. Dedicated application retry/backoff and
dead-letter publishing are not implemented. See
[architecture](../architecture.md#important-limitations) for other limits.
