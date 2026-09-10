# Scraper Fixtures

Use this folder for deterministic parser fixtures.

## Layout

- `scrapers/{companyId}/search/` for search/list responses
- `scrapers/{companyId}/detail/` for job detail responses

## Naming convention

- Search pages: `search-page-{n}.json` or `search-page-{n}.html`
- Detail pages: `job-{externalJobId}.html`
- Optional metadata: `run-summary.json`

Keep fixtures sanitized and stable so parser tests do not depend on live websites.

