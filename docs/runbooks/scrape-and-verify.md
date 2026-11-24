# Scrape and verify

## Trigger a run

The timer queues commands for every enabled company. To trigger manually, use
the `OnDemandScrapeCommandProducer` HTTP function, which accepts `companyId` as a query
parameter: `mckesson`, `gm-financial`, `first-citizens`, `frost-bank`, or
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
    $companyIds = @('mckesson', 'gm-financial', 'first-citizens', 'frost-bank')
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
[CI/CD reference](../../.github/workflows/README.md). Its scrape trace lookup
matches the company and a recent time window, not the returned run ID. Its
notification check matches the run ID but only establishes that the notification
consumer logged processing; it can also succeed for a deduplicated or logged
notification. The email check is skipped when disabled or when no run ID is
returned. None of these checks proves inbox delivery or database persistence.

## Check processing and email

1. Use the run IDs and company IDs in the trigger responses to locate the
   corresponding function logs. Check command consumption, scrape completion,
   page counts, and any collected errors for each company.
2. Check normalization, persistence, metrics, and notification logs separately.
   A notification is published without waiting for persistence; a sent email
   does not establish that the database has been updated.
3. Check the email provider's delivery status and the recipient's inbox. Email
   provider acceptance alone is not proof of inbox delivery. A partial scrape
   can still send an email with failures included.

Avoid posting logs or screenshots containing keys, account identifiers, email
addresses, job applicant data, or other private information.

## Read saved jobs and raw details

In the Azure portal, open your Cosmos DB account, then **Data Explorer** and
the configured database. Open `job-postings` to inspect normalized documents,
`job-observations` for dated sightings, and `monthly-metrics` for aggregates.
For example, query the postings container:

```sql
SELECT c.companyId, c.externalJobId, c.title, c.location, c.datePosted, c.url
FROM c
WHERE c.companyId = "mckesson"
```

Use the configured database and container names if they differ from the
defaults. Full descriptions and salary are not fields in the normalized
posting. In your storage account, use **Storage browser** or Azure Storage
Explorer to browse the configured raw container by company, host-local date, and run
ID. Open the search and fetched detail files for source fields and
descriptions. Some details are absent when the detail-page cap or a request
failure prevented fetching them.

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
