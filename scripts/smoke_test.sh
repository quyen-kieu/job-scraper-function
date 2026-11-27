#!/usr/bin/env bash
# Deployment smoke test; see docs/runbooks/scrape-and-verify.md for its limitations.
#
# Triggers a REAL on-demand scrape for a designated smoke-test company via the deployed
# OnDemandScrapeCommandProducer HTTP endpoint, then polls Application Insights for the
# run-correlated scrape completion and Resend acceptance traces.
#
# It hits the real company careers site, not a mock; repeated runs can create rate-limit concerns.
#
# Required environment variables:
#   RESOURCE_GROUP            e.g. java-functions-group
#   FUNCTION_APP_NAME         e.g. job-scraper-function-prod
#   APP_INSIGHTS_APP_ID       Application Insights "Application ID" (not the connection string)
#   SMOKE_TEST_COMPANY_ID     defaults to "mckesson"
#   SMOKE_TEST_TIMEOUT_SECS   defaults to 300 (5 minutes)
#   SMOKE_TEST_POLL_SECS      defaults to 15
#   SMOKE_TEST_VERIFY_EMAIL   defaults to true; set false to skip the email check
#
# Requires: az CLI logged in (azure/login in the calling workflow), curl, jq.

set -euo pipefail

RESOURCE_GROUP="${RESOURCE_GROUP:?RESOURCE_GROUP is required}"
FUNCTION_APP_NAME="${FUNCTION_APP_NAME:?FUNCTION_APP_NAME is required}"
APP_INSIGHTS_APP_ID="${APP_INSIGHTS_APP_ID:?APP_INSIGHTS_APP_ID is required}"
SMOKE_TEST_COMPANY_ID="${SMOKE_TEST_COMPANY_ID:-mckesson}"
SMOKE_TEST_TIMEOUT_SECS="${SMOKE_TEST_TIMEOUT_SECS:-300}"
SMOKE_TEST_POLL_SECS="${SMOKE_TEST_POLL_SECS:-15}"
SMOKE_TEST_VERIFY_EMAIL="${SMOKE_TEST_VERIFY_EMAIL:-true}"

if [[ ! "${SMOKE_TEST_COMPANY_ID}" =~ ^[a-z0-9][a-z0-9-]{0,49}$ ]]; then
  echo "ERROR: invalid smoke-test company ID." >&2
  exit 1
fi
if [[ "${SMOKE_TEST_VERIFY_EMAIL}" != "true" && "${SMOKE_TEST_VERIFY_EMAIL}" != "false" ]]; then
  echo "ERROR: SMOKE_TEST_VERIFY_EMAIL must be true or false." >&2
  exit 1
fi
if [[ ! "${SMOKE_TEST_TIMEOUT_SECS}" =~ ^[1-9][0-9]*$ || ! "${SMOKE_TEST_POLL_SECS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "ERROR: smoke-test timeout and poll interval must be positive integers." >&2
  exit 1
fi

echo "== Stage 8 smoke test: on-demand scrape for company '${SMOKE_TEST_COMPANY_ID}' =="

if [[ "${SMOKE_TEST_VERIFY_EMAIL}" == "true" ]]; then
  if ! RESEND_ENABLED=$(az functionapp config appsettings list \
    --resource-group "${RESOURCE_GROUP}" \
    --name "${FUNCTION_APP_NAME}" \
    --query "[?name=='RESEND_ENABLED'].value | [0]" -o tsv); then
    echo "ERROR: could not check whether Resend is enabled." >&2
    exit 1
  fi
  if [[ "${RESEND_ENABLED,,}" != "true" ]]; then
    echo "ERROR: RESEND_ENABLED must be true to verify an email send." >&2
    exit 1
  fi
fi

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

# For Flex Consumption apps, `az functionapp show` returns the top-level `defaultHostName` as
# null; the value only lives under `properties.defaultHostName` (verified against the live app).
# An empty host made curl fail with exit code 6 ("could not resolve host") and, under `set -e`,
# abort the script silently. Prefer the properties path, fall back to the top-level field.
FUNC_HOST=$(az functionapp show \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${FUNCTION_APP_NAME}" \
  --query "properties.defaultHostName || defaultHostName" -o tsv)

if [[ -z "${FUNC_HOST}" || "${FUNC_HOST}" == "null" ]]; then
  echo "ERROR: could not resolve default hostname for Function App '${FUNCTION_APP_NAME}'" >&2
  exit 1
fi
echo "-- Function App host: ${FUNC_HOST}"

# Pre-flight: the Functions host must be Running, otherwise no functions are registered and the
# trigger returns 404. Surface the host's own error (e.g. missing Kafka SASL credentials).
MASTER_KEY=$(az functionapp keys list \
  --resource-group "${RESOURCE_GROUP}" \
  --name "${FUNCTION_APP_NAME}" \
  --query "masterKey" -o tsv)
if [[ -z "${MASTER_KEY}" || "${MASTER_KEY}" == "null" ]]; then
  echo "ERROR: could not retrieve a Functions host key." >&2
  exit 1
fi
HOST_STATE=""
for _ in 1 2 3 4 5 6; do
  HOST_STATUS_JSON=$(curl -sS --max-time 120 -H "x-functions-key: ${MASTER_KEY}" "https://${FUNC_HOST}/admin/host/status" || echo "{}")
  HOST_STATE=$(echo "${HOST_STATUS_JSON}" | jq -r '.state // empty' 2>/dev/null || true)
  [[ "${HOST_STATE}" == "Running" ]] && break
  echo "   host state='${HOST_STATE:-unknown}', retrying in 20s..."
  sleep 20
done
if [[ "${HOST_STATE}" != "Running" ]]; then
  echo "ERROR: Functions host is not Running (state='${HOST_STATE:-unknown}'). Host errors:" >&2
  echo "${HOST_STATUS_JSON}" | jq -r '.errors // [] | .[]' >&2 2>/dev/null || true
  exit 1
fi
echo "-- Functions host is Running."

ENDPOINT="https://${FUNC_HOST}/api/scrape/trigger?companyId=${SMOKE_TEST_COMPANY_ID}"
RESPONSE_FILE=$(mktemp)
trap 'rm -f -- "${RESPONSE_FILE}"' EXIT

echo "-- Triggering on-demand scrape via POST /api/scrape/trigger?companyId=${SMOKE_TEST_COMPANY_ID} ..."
CURL_EXIT=0
HTTP_STATUS=$(curl -sS --max-time 120 \
  -H "x-functions-key: ${FUNCTION_KEY}" -o "${RESPONSE_FILE}" -w "%{http_code}" -X POST "${ENDPOINT}") || CURL_EXIT=$?

if [[ "${CURL_EXIT}" -ne 0 ]]; then
  echo "ERROR: curl failed with exit code ${CURL_EXIT} calling https://${FUNC_HOST}/api/scrape/trigger" >&2
  exit 1
fi

if [[ "${HTTP_STATUS}" != "200" ]]; then
  echo "ERROR: on-demand trigger returned HTTP ${HTTP_STATUS}. Response body:" >&2
  cat "${RESPONSE_FILE}" >&2 || true
  exit 1
fi

RUN_ID=$(jq -r '.runId // empty' "${RESPONSE_FILE}" 2>/dev/null || true)
if [[ ! "${RUN_ID}" =~ ^[a-zA-Z0-9-]{1,128}$ ]]; then
  echo "ERROR: trigger response did not contain a valid run ID; cannot correlate smoke-test traces." >&2
  exit 1
fi
echo "-- Triggered successfully. runId=${RUN_ID}"

echo "-- Polling Application Insights for the scrape-completed trace (timeout ${SMOKE_TEST_TIMEOUT_SECS}s)..."
DEADLINE=$(( $(date +%s) + SMOKE_TEST_TIMEOUT_SECS ))
KQL="traces | where timestamp > ago(1h) | where message contains 'Scrape completed for company ${SMOKE_TEST_COMPANY_ID}:' | where message contains ' runId=${RUN_ID} ' | project timestamp, message | take 1"

while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
  if ! RESULT=$(az monitor app-insights query \
    --app "${APP_INSIGHTS_APP_ID}" \
    --analytics-query "${KQL}" \
    --query "tables[0].rows" -o tsv); then
    echo "WARNING: Application Insights scrape-trace query failed; retrying." >&2
    RESULT=""
  fi

  if [[ -n "${RESULT}" ]]; then
    echo "-- Found expected trace: ${RESULT}"
    SCRAPE_TRACE="${RESULT}"
    SCRAPE_OK=1
    break
  fi

  echo "   ...not yet observed, retrying in ${SMOKE_TEST_POLL_SECS}s"
  sleep "${SMOKE_TEST_POLL_SECS}"
done

if [[ "${SCRAPE_OK:-0}" -ne 1 ]]; then
  echo "ERROR: timed out after ${SMOKE_TEST_TIMEOUT_SECS}s waiting for the scrape-completed trace." >&2
  echo "This means the event path (HTTP trigger -> job.scrape.commands -> ScraperConsumerFunction)" >&2
  echo "did not complete as expected. Check Application Insights and the Function App's logs." >&2
  exit 1
fi

# Phase 2: verify the notification leg (normalizer -> notifications.daily -> ResendEmailConsumerFunction).
# Require the sender's successful Resend response, not the consumer's processed/deduplicated trace.
if [[ "${SMOKE_TEST_VERIFY_EMAIL}" == "false" ]]; then
  echo "== Smoke test PASSED: event path (HTTP trigger -> Kafka -> scrape) verified (email check skipped). =="
  exit 0
fi

echo "-- Polling Application Insights for the email-sent trace for runId=${RUN_ID}..."
EMAIL_KQL="traces | where timestamp > ago(1h) | where message contains 'Sent daily notification email runId=${RUN_ID} summaryDate=' | project timestamp, message | take 1"
DEADLINE=$(( $(date +%s) + SMOKE_TEST_TIMEOUT_SECS ))
while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
  if ! RESULT=$(az monitor app-insights query \
    --app "${APP_INSIGHTS_APP_ID}" \
    --analytics-query "${EMAIL_KQL}" \
    --query "tables[0].rows" -o tsv); then
    echo "WARNING: Application Insights email-trace query failed; retrying." >&2
    RESULT=""
  fi

  if [[ -n "${RESULT}" ]]; then
    echo "-- Found expected trace: ${RESULT}"
    EMAIL_OK=1
    break
  fi

  echo "   ...not yet observed, retrying in ${SMOKE_TEST_POLL_SECS}s"
  sleep "${SMOKE_TEST_POLL_SECS}"
done

if [[ "${EMAIL_OK:-0}" -ne 1 ]]; then
  echo "ERROR: timed out after ${SMOKE_TEST_TIMEOUT_SECS}s waiting for the email-sent trace for runId=${RUN_ID}." >&2
  echo "Check NormalizerConsumerFunction / ResendEmailConsumerFunction failures in Application Insights." >&2
  exit 1
fi

if [[ "${SCRAPE_TRACE}" != *" searchComplete=true "* || ! "${SCRAPE_TRACE}" =~ [[:space:]]errors=0($|[[:space:]]) ]]; then
  echo "ERROR: the smoke scrape was incomplete or reported errors for runId=${RUN_ID}." >&2
  exit 1
fi
if [[ "${SCRAPE_TRACE}" == *" fullDetailRefresh=true "* ]]; then
  echo "-- Polling Application Insights for successful full reconciliation for runId=${RUN_ID}..."
  RECONCILE_KQL="traces | where timestamp > ago(1h) | where message contains 'Reconciliation completed companyId=${SMOKE_TEST_COMPANY_ID} runId=${RUN_ID} ' | project timestamp, message | take 1"
  DEADLINE=$(( $(date +%s) + SMOKE_TEST_TIMEOUT_SECS ))
  while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
    if ! RESULT=$(az monitor app-insights query \
      --app "${APP_INSIGHTS_APP_ID}" \
      --analytics-query "${RECONCILE_KQL}" \
      --query "tables[0].rows" -o tsv); then
      echo "WARNING: Application Insights reconciliation-trace query failed; retrying." >&2
      RESULT=""
    fi
    if [[ -n "${RESULT}" ]]; then
      echo "-- Found expected trace: ${RESULT}"
      RECONCILE_OK=1
      break
    fi
    echo "   ...not yet observed, retrying in ${SMOKE_TEST_POLL_SECS}s"
    sleep "${SMOKE_TEST_POLL_SECS}"
  done
  if [[ "${RECONCILE_OK:-0}" -ne 1 ]]; then
    echo "ERROR: timed out waiting for successful full reconciliation for runId=${RUN_ID}." >&2
    exit 1
  fi
elif [[ "${SCRAPE_TRACE}" != *" fullDetailRefresh=false "* ]]; then
  echo "ERROR: smoke scrape did not report a valid hybrid refresh mode for runId=${RUN_ID}." >&2
  exit 1
fi

echo "== Smoke test PASSED: scrape completed and Resend accepted the email for this run; inbox delivery is not verified. =="
