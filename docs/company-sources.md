# Company sources

Each enabled company has a registered scraper. The table describes the
intended search, not a guarantee of complete or current listings: the sites can
change and requests may fail or be capped.

| Company | Company ID | Source and implementation |
| --- | --- | --- |
| McKesson | `mckesson` | [Careers search](https://careers.mckesson.com/en/search-jobs) using the [McKesson scraper](../src/main/java/com/jobscraper/scraper/companies/mckesson/McKessonScraper.java). The implemented search request sends an empty keyword, location, and facet; it does not specifically filter for Software Engineer. |
| GM Financial | `gm-financial` | `software development` search via [Oracle Recruiting Cloud](../src/main/java/com/jobscraper/scraper/companies/oracle/OracleRecruitingScraper.java), configured in [GM Financial settings](../src/main/java/com/jobscraper/scraper/companies/gmfinancial/GmFinancialScraperConfiguration.java) |
| First Citizens Bank | `first-citizens` | [Information Technology jobs](https://firstcitizens.jibeapply.com/jobs?page=1&categories=Information%20Technology&sortBy=posted_date&descending=true) using the [First Citizens scraper](../src/main/java/com/jobscraper/scraper/companies/firstcitizens/FirstCitizensScraper.java) |
| Frost Bank | `frost-bank` | [Technology and Digital jobs](https://careers.frostbank.com/us/en/c/technology-digital-jobs) using the [Frost Bank scraper](../src/main/java/com/jobscraper/scraper/companies/frostbank/FrostBankScraper.java) |

Company enablement and page limits are configurable; see
[`local.settings.sample.json`](../local.settings.sample.json). Defaults cap a
company run at three fetched search pages and ten fetched detail pages, with a
300 ms request delay. First Citizens does not request separate detail pages:
its search responses contain the descriptions used for salary extraction.
Frost Bank limits detail **attempts**, while McKesson and GM Financial limit
successful detail fetches; failures can therefore lead to more than ten
detail requests for those two sources. Each limit applies per company, not
across the entire daily run.

Frost Bank results use the source's default ordering, not a guaranteed newest
first order. The search-page cap can exclude later listings. Verify the source
site and adjust the caps responsibly when coverage matters.
