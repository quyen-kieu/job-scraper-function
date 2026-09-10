#!/usr/bin/env bash
# Stage 8, README item 12: "Run a smoke test that publishes a test command and verifies the
# expected event path."
#
# Triggers a REAL on-demand scrape for a designated smoke-test company via the deployed
# OnDemandScrapeCommandProducer HTTP endpoint, then polls Application Insights for the
# "Scrape completed for company <id>" trace line that ScraperConsumerFunction logs on success
# (see src/main/java/com/jobscraper — the exact log line observed during Stage 6 manual testing).
#
# This is a deliberate scope decision (see docs/STAGE8_PLAN.md §4.4): it hits the real company
# careers site, not a mock. Acceptable at this project's scale (single company today,
# JOB_SCRAPER_REQUEST_DELAY throttling, single-partition topics) — reconsider if company
# count/frequency grows enough to make repeated CI-triggered scrapes a rate-limit concern.
#
# Required environment variables:
#   RESOURCE_GROUP            e.g. java-functions-group
#   FUNCTION_APP_NAME         e.g. job-scraper-function-prod
#   APP_INSIGHTS_APP_ID       Application Insights "Application ID" (not the connection string)
#   SMOKE_TEST_COMPANY_ID     defaults to "mckesson" (the only company configured today)
#   SMOKE_TEST_TIMEOUT_SECS   defaults to 300 (5 minutes)
#   SMOKE_TEST_POLL_SECS      defaults to 15
#
# Requires: az CLI logged in (azure/login in the calling workflow), curl, jq.

set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:?RESOURCE_GROUP is required}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?FUNCTION_APP_NAME is required}"
APP_INSIGHTS_APP_ID="${APP_INSIGHTS_APP_ID:?APP_INSIGHTS_APP_ID is required}"
SMOKE_TEST_COMPANY_ID="${SMOKE_TEST_COMPANY_ID:-mckesson}"
SMOKE_TEST_TIMEOUT_SECS="${SMOKE_TEST_TIMEOUT_SECS:-300}"
SMOKE_TEST_POLL_SECS="${SMOKE_TEST_POLL_SECS:-15}"

echo "== Stage 8 smoke test: on-demand scrape for company '${SMOKE_TEST_COMPANY_ID}' =="

echo "-- Fetching OnDemandScrapeCommandProducer function key..."
FUNCTION_KEY=$(az functionapp function keys list \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${FUNCTION_APP_NAME}" \
  --function-name OnDemandScrapeCommandProducer \
  --query "default" -o tsv)

if [[ -z "${FUNCTION_KEY}" || "${FUNCTION_KEY}" == "null" ]]; then
  echo "ERROR: could not retrieve a function key for OnDemandScrapeCommandProducer" >&2
  exit 1
fi

HOSTNAME=$(az functionapp show \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${FUNCTION_APP_NAME}" \
  --query "defaultHostName" -o tsv)

ENDPOINT="https://${HOSTNAME}/api/scrape/trigger?companyId=${SMOKE_TEST_COMPANY_ID}&code=${FUNCTION_KEY}"

echo "-- Triggering on-demand scrape via POST /api/scrape/trigger?companyId=${SMOKE_TEST_COMPANY_ID} ..."
HTTP_STATUS=$(curl -s -o /tmp/smoke-test-response.json -w "%{http_code}" -X POST "${ENDPOINT}")

if [[ "${HTTP_STATUS}" != "200" ]]; then
  echo "ERROR: on-demand trigger returned HTTP ${HTTP_STATUS}. Response body:" >&2
  cat /tmp/smoke-test-response.json >&2 || true
  exit 1
fi

RUN_ID=$(jq -r '.runId // empty' /tmp/smoke-test-response.json 2>/dev/null || true)
echo "-- Triggered successfully. runId=${RUN_ID:-<unknown>}"

echo "-- Polling Application Insights for the scrape-completed trace (timeout ${SMOKE_TEST_TIMEOUT_SECS}s)..."
DEADLINE=$(( $(date +%s) + SMOKE_TEST_TIMEOUT_SECS ))
KQL="traces | where message contains 'Scrape completed for company ${SMOKE_TEST_COMPANY_ID}' | where timestamp > ago(10m) | order by timestamp desc | take 1"

while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
  RESULT=$(az monitor app-insights query \
    --app "${APP_INSIGHTS_APP_ID}" \
    --analytics-query "${KQL}" \
    --query "tables[0].rows" -o tsv 2>/dev/null || echo "")

  if [[ -n "${RESULT}" ]]; then
    echo "-- Found expected trace: ${RESULT}"
    echo "== Smoke test PASSED: full event path (HTTP trigger -> Kafka -> scrape) verified. =="
    exit 0
  fi

  echo "   ...not yet observed, retrying in ${SMOKE_TEST_POLL_SECS}s"
  sleep "${SMOKE_TEST_POLL_SECS}"
done

echo "ERROR: timed out after ${SMOKE_TEST_TIMEOUT_SECS}s waiting for the scrape-completed trace." >&2
echo "This means the event path (HTTP trigger -> job.scrape.commands -> ScraperConsumerFunction)" >&2
echo "did not complete as expected. Check Application Insights and the Function App's logs." >&2
exit 1


