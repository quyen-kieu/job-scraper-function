# Company sources

Each enabled company has a registered scraper. The table describes the
intended search, not a guarantee of complete or current listings: the sites can
change and requests may fail or be capped.

| Company | Company ID | Source and implementation |
| --- | --- | --- |
| McKesson | `mckesson` | [Workday search](https://mckesson.wd3.myworkdayjobs.com/en-US/External_Careers?Location_Region_State_Province=fc77e3a1ab36487f9646d14f7242dd77&jobFamilyGroup=29ce91b0412201aafb7bf4bd4f169117&Location_Country=bc33aa3152ec42d4995f4791a106ed09) using the [McKesson scraper](../src/main/java/com/jobscraper/scraper/companies/mckesson/McKessonScraper.java). It filters for Texas, Technology, and United States, not a particular job title. |
| GM Financial | `gm-financial` | `software development` search via [Oracle Recruiting Cloud](../src/main/java/com/jobscraper/scraper/companies/oracle/OracleRecruitingScraper.java), configured in [GM Financial settings](../src/main/java/com/jobscraper/scraper/companies/gmfinancial/GmFinancialScraperConfiguration.java) |
| First Citizens Bank | `first-citizens` | [Information Technology jobs](https://firstcitizens.jibeapply.com/jobs?page=1&categories=Information%20Technology&sortBy=posted_date&descending=true) using the [First Citizens scraper](../src/main/java/com/jobscraper/scraper/companies/firstcitizens/FirstCitizensScraper.java) |
| Frost Bank | `frost-bank` | [Technology and Digital jobs](https://careers.frostbank.com/us/en/c/technology-digital-jobs) using the [Frost Bank scraper](../src/main/java/com/jobscraper/scraper/companies/frostbank/FrostBankScraper.java) |

Company enablement and page limits are configurable; see
[`local.settings.sample.json`](../local.settings.sample.json). By default each
company run fetches up to three search pages with a 300 ms request delay.
McKesson, GM Financial, and Frost Bank attempt a detail request for every
distinct job discovered within those pages, including after individual failures.
First Citizens instead extracts descriptions from search responses. There is
no detail-page cap. Larger runs require more time and requests and can incur
additional storage and source-site traffic.

McKesson's Workday facet IDs can be overridden with
`JOB_SCRAPER_MCKESSON_STATE_FACET_ID`, `JOB_SCRAPER_MCKESSON_JOB_FAMILY_FACET_ID`,
and `JOB_SCRAPER_MCKESSON_COUNTRY_FACET_ID` in the Function App settings or local
settings. For deployed environments, also update the corresponding Bicep parameters
in [`main.bicep`](../infra/azure/main.bicep) or the environment parameter file so
the next deployment preserves the change. They identify Workday filter values, not credentials. They generally
remain associated with their Workday objects, but McKesson can change its site
configuration or recreate values; verify the current filters on the site if
requests fail or results unexpectedly disappear. Do not assume the IDs are permanent.

Workday's search uses requisition IDs such as `JR...`, whereas the previous
McKesson source used different IDs. Existing Cosmos documents are not migrated
or removed, and matching jobs may be classified as new after the switch.
If Workday omits the search result's requisition ID, the scraper uses an
unambiguous `JR`-number suffix from its job path; results without either are
reported as errors.
The search returns relative dates; the scraper estimates exact "today",
"yesterday", and "N days ago" values using UTC. "30+ days ago" is left unknown
unless a detail response supplies an absolute date; the normalizer substitutes
the current date when the scraper leaves a date unknown. Detail requests obtain
descriptions, salary, and absolute dates when available. Jobs with multiple
locations can include locations outside Texas.

Frost Bank results use the source's default ordering, not a guaranteed newest
first order. The search-page cap can exclude later listings. Verify the source
site and adjust the search-page cap responsibly when coverage matters.
