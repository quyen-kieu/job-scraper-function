# Company sources

Each enabled company has a registered scraper. The table describes the
intended search, not a guarantee of complete or current listings: the sites can
change and requests may fail or be capped.

| Company | Company ID | Source and implementation |
| --- | --- | --- |
| McKesson | `mckesson` | [Workday search](https://mckesson.wd3.myworkdayjobs.com/en-US/External_Careers?Location_Region_State_Province=fc77e3a1ab36487f9646d14f7242dd77&jobFamilyGroup=29ce91b0412201aafb7bf4bd4f169117&Location_Country=bc33aa3152ec42d4995f4791a106ed09) using the [McKesson scraper](../src/main/java/com/jobscraper/scraper/companies/mckesson/McKessonScraper.java). It filters for Texas, Technology, and United States, not a particular job title. |
| GM Financial | `gm-financial` | `software development` search via [Oracle Recruiting Cloud](../src/main/java/com/jobscraper/scraper/companies/oracle/OracleRecruitingScraper.java), configured in [GM Financial settings](../src/main/java/com/jobscraper/scraper/companies/gmfinancial/GmFinancialScraperConfiguration.java) |
| First Citizens Bank | `first-citizens` | [Information Technology jobs](https://firstcitizens.jibeapply.com/jobs?page=1&categories=Information%20Technology&sortBy=posted_date&descending=true) using the [First Citizens scraper](../src/main/java/com/jobscraper/scraper/companies/firstcitizens/FirstCitizensScraper.java) |
| Frost Bank | `frost-bank` | [Workday Technology and Digital jobs](https://frostbank.wd5.myworkdayjobs.com/External/?jobFamily=3a9cae9f1324015f492308c79dc96d35&locationHierarchy1=8fe73009103a424c89e0c6c010a6d8f3&locationHierarchy1=58c99fe2f997418f8c54b3ebcf1b6a5d) using the [Frost Bank scraper](../src/main/java/com/jobscraper/scraper/companies/frostbank/FrostBankScraper.java). |
| CVS Health | `cvs` | [Workday search](https://cvshealth.wd1.myworkdayjobs.com/CVS_Health_Careers?source=CareerMarketingSite&jobFamilyGroup=e65dbadf6a50100168ed86fe4cf50001&locations=69b7f7384b5e1008e9c744772f180000&locations=e75b3cfda6d41000edbef9d1f17b0000&locations=e75b3cfda6d41000edbdcbb1c2b50000&locations=e75b3cfda6d41000edbc9e0488440000&locations=e75b3cfda6d41000edc098b35b130000&locations=e75b3cfda6d41000edc32657f9160000) using the [CVS scraper](../src/main/java/com/jobscraper/scraper/companies/cvs/CvsScraper.java). |
| Citi | `citi` | [Workday search](https://citi.wd5.myworkdayjobs.com/2/?jobFamilyGroup=e32326e1708d01575bddff0c120102c1&workerSubType=e32326e1708d01289a50257249019cdd&Country_and_Jurisdiction=bc33aa3152ec42d4995f4791a106ed09&State_or_Province=fc77e3a1ab36487f9646d14f7242dd77) using the [Citi scraper](../src/main/java/com/jobscraper/scraper/companies/citi/CitiScraper.java). |
| Fannie Mae | `fannie-mae` | [Workday search](https://fanniemae.wd1.myworkdayjobs.com/FannieMaeCareers/?remoteType=bf1dc34adb121001727a83cb88650000&jobFamilyGroup=bf1dc34adb12100175ce6a6921f70000&locations=464222325cc3100210ee7e4b71320000) using the [Fannie Mae scraper](../src/main/java/com/jobscraper/scraper/companies/fanniemae/FannieMaeScraper.java). |

Company enablement and page limits are configurable; see
[`local.settings.sample.json`](../local.settings.sample.json). By default each
company run fetches up to three search pages with a 300 ms request delay.
McKesson, GM Financial, Frost Bank, CVS, Citi, and Fannie Mae attempt a detail request for every
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

CVS uses the selected Workday job-family group and all six location IDs from
the search link. The `source` parameter is a marketing attribution parameter,
not a job filter. Override the IDs with `JOB_SCRAPER_CVS_JOB_FAMILY_FACET_ID`
and comma-separated `JOB_SCRAPER_CVS_LOCATION_FACET_IDS`; in deployed
environments also update the corresponding Bicep parameters. Workday IDs can
change if CVS reconfigures its site. The scraper uses Workday requisition IDs
such as `R1097216`, falling back to an unambiguous ID in the job path when
necessary. Detail responses provide descriptions, salary, and absolute dates
when available; multiple-location results can include other locations.

Frost Bank's selected job-family and two location IDs can be overridden with
`JOB_SCRAPER_FROST_BANK_JOB_FAMILY_FACET_ID` and comma-separated
`JOB_SCRAPER_FROST_BANK_LOCATION_FACET_IDS`. The original supplied job-family ID
was rejected by Workday; the configured ID is its current Technology and Digital
facet. The old and new sources use `R...` requisition IDs, so matching IDs can
reuse existing postings. A different title, location, or date can still classify
the posting as changed. Workday returns up to 20 results per page instead of
the old source's 10.

Citi's filters can be overridden with `JOB_SCRAPER_CITI_JOB_FAMILY_FACET_ID`,
`JOB_SCRAPER_CITI_WORKER_SUBTYPE_FACET_ID`, `JOB_SCRAPER_CITI_COUNTRY_FACET_ID`,
and `JOB_SCRAPER_CITI_STATE_FACET_ID`. Fannie Mae's filters can be overridden
with `JOB_SCRAPER_FANNIE_MAE_REMOTE_TYPE_FACET_ID`,
`JOB_SCRAPER_FANNIE_MAE_JOB_FAMILY_FACET_ID`, and
`JOB_SCRAPER_FANNIE_MAE_LOCATION_FACET_ID`. For deployed environments, update
the corresponding Bicep parameters as well. These Workday facet IDs may change.
Multi-location jobs can include locations outside the selected filters. Citi
and Fannie Mae use their Workday requisition IDs; where the ID is absent, an
unambiguous ID may be extracted from the job path.
