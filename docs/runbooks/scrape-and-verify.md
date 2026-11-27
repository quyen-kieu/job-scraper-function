# Scrape and verify

## Trigger a run

The timer queues commands for every enabled company. To trigger manually, use
the `OnDemandScrapeCommandProducer` HTTP function, which accepts `companyId` as a query
parameter: `mckesson`, `gm-financial`, `first-citizens`, `frost-bank`, `cvs`,
`citi`, `fannie-mae`, `humana`, `capital-one`, or
omit the parameter to queue all currently enabled companies. A successful
response does not guarantee broker acknowledgment, processing, or email.

For a deployed Function App, use its base URL and a function key from your
own environment. This PowerShell example prompts for the key without echoing
it and sends it in a header rather than placing it in a URL:

```powershell
$baseUri = (Read-Host "Function App base URL").TrimEnd('/')
$secureKey = Read-Host "Function key" -AsSecureString
$key = [System.Net.NetworkCredential]::new('', $secureKey).Password
try {
    $companyIds = @('mckesson', 'gm-financial', 'first-citizens', 'frost-bank', 'cvs', 'citi', 'fannie-mae', 'humana', 'capital-one')
    foreach ($companyId in $companyIds) {
        Invoke-RestMethod -Method Post `
            -Uri "$baseUri/api/scrape/trigger?companyId=$companyId" `
            -Headers @{ 'x-functions-key' = $key }
    }
}
finally {
    Remove-Variable key, secureKey -ErrorAction SilentlyContinue
}
```

Trigger only companies that are enabled and that you intend to run; an individual request for a
disabled company is rejected. The separate requests above make it
easier to correlate each returned run ID; omit `companyId` for a single
request covering enabled companies. The all-company response reports the
number of commands but does not return the shared run ID. The local Functions host normally disables
key authorization unless explicitly started with authorization enabled.

The deployment smoke test defaults to **McKesson only**. A successful
deployment smoke test does not mean the other companies were triggered. See
[`scripts/smoke_test.sh`](../../scripts/smoke_test.sh) and the
[CI/CD reference](../../.github/workflows/README.md). The smoke test correlates
the scrape and Resend acceptance traces to the returned run ID and fails if
the ID is missing or Resend is disabled. `SMOKE_TEST_VERIFY_EMAIL=false` explicitly
skips the email and reconciliation checks. Otherwise, the smoke test also requires
an error-free complete search and, for a full refresh, a successful reconciliation
trace for that run. Resend acceptance does not prove inbox delivery.

## Check processing and email

1. Use the run IDs and company IDs in the trigger responses to locate the
   corresponding function logs. Check command consumption, scrape completion,
   page counts, full-refresh and completeness flags, and error counts for each
   company. Workday search traces also report the first-page total, unique jobs,
   total consistency, and whether the end was reached. Search failures log the
   page, failure category, and HTTP status when available without logging raw
   exception details. Refer to the notification summary for collected errors.
2. Check normalization, persistence, metrics, and notification logs separately.
   The normalizer persists postings and observations before publishing the
   notification. A skipped or failed reconciliation is reported in the summary;
   verify actual postings independently.
3. Check the email provider's delivery status and the recipient's inbox. Email
   provider acceptance alone is not proof of inbox delivery. A partial scrape
   can still send an email with failures included.

Avoid posting logs or screenshots containing keys, account identifiers, email
addresses, job applicant data, or other private information.

## Read saved jobs

In the Azure portal, open your Cosmos DB account, then **Data Explorer** and
the configured database. Open `job-postings` to inspect normalized documents,
`job-observations` for dated sightings, and `monthly-metrics` for aggregates.
For example, query the postings container:

```sql
SELECT c.companyId, c.externalJobId, c.titleRaw, c.location, c.datePosted, c.url,
       c.descriptionHtml, c.descriptionPlainText
FROM c
WHERE c.companyId = "mckesson" AND IS_DEFINED(c.externalJobId)
```

Use the configured database and container names if they differ from the
defaults. `descriptionHtml` contains source HTML where available, and
`descriptionPlainText` contains readable text. Older documents may contain only
the legacy `description` field until they are next updated. Treat HTML as
untrusted and sanitize it before rendering.
`salaryRange` is populated from details when available; older documents or jobs with missing
or failed detail requests may have no description. A successful future scrape
after deployment can populate discovered jobs. A configured positive page cap
can prevent a complete backfill and reconciliation.

To migrate legacy `description` fields on records that are not revisited, set
`AZURE_COSMOS_ENDPOINT`, `AZURE_COSMOS_KEY`, `AZURE_COSMOS_DATABASE_NAME`, and
`AZURE_COSMOS_POSTINGS_CONTAINER` in a trusted process environment. Run
`mvn -q compile exec:java "-Dexec.mainClass=com.jobscraper.tools.MigrateLegacyDescriptions"`
for a read-only count. After reviewing backups and the result, add
`"-Dexec.args=--apply"` to copy legacy text where needed and remove the old field.
The tool uses an ETag condition to avoid overwriting concurrent changes; rerun
if it reports conflicts. Do not expose keys in logs or shell history.

The production scraper does not archive raw responses. The `description-handoffs`
Cosmos container holds temporary source descriptions for 30 days after their
last modification; the final posting is independent of the handoff's expiry.

For local file mode, browse the ignored `output/raw` directory instead.
See [architecture](../architecture.md#stored-data) for the layouts and
storage semantics.

## Cost and access

Azure Cosmos DB for NoSQL offers an account-level free tier, subject to Azure's
eligibility and limits. It includes a provisioned throughput and storage
allowance; usage beyond the allowance and other Azure services can incur
charges. Check the account's free-tier status and current
[official pricing](https://azure.microsoft.com/pricing/details/cosmos-db/autoscale-provisioned/)
before relying on it. Use least-privilege access and avoid exporting private
data to public locations.
