#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -f -- "${TEST_DIR}/az" "${TEST_DIR}/curl" "${TEST_DIR}/jq" "${TEST_DIR}/output"; rmdir -- "${TEST_DIR}"' EXIT

cat > "${TEST_DIR}/az" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  *"functionapp config appsettings list"*)
    if [[ "${MOCK_SCENARIO}" == "disabled" ]]; then echo false; else echo true; fi ;;
  *"functionapp function keys list"*) echo fake-function-key ;;
  *"functionapp keys list"*) echo fake-master-key ;;
  *"functionapp show"*) echo example.test ;;
  *"monitor app-insights query"*)
    query=""
    while [[ $# -gt 0 ]]; do
      if [[ "$1" == "--analytics-query" ]]; then query="$2"; break; fi
      shift
    done
    if [[ "${query}" == *"Scrape completed for company mckesson:"* ]]; then
      if [[ "${MOCK_SCENARIO}" != "wrong-run" && "${query}" == *" runId=run-123 "* ]]; then
        complete=true
        errors=0
        refresh=true
        [[ "${MOCK_SCENARIO}" == "incomplete" ]] && complete=false
        [[ "${MOCK_SCENARIO}" == "scrape-error" ]] && errors=1
        [[ "${MOCK_SCENARIO}" == "daily" ]] && refresh=false
        echo "Scrape completed for company mckesson: 3 search pages, 60 detail pages runId=run-123 searchComplete=${complete} fullDetailRefresh=${refresh} uniqueJobs=60 errors=${errors}"
      elif [[ "${query}" != *" runId=run-123 "* ]]; then
        echo "Scrape completed for company mckesson: wrong run"
      fi
    elif [[ "${query}" == *"Sent daily notification email runId=run-123 summaryDate="* ]]; then
      if [[ "${MOCK_SCENARIO}" != "processed-only" ]]; then
        echo "Sent daily notification email runId=run-123 summaryDate=2026-01-01 resendId=fake"
      fi
    elif [[ "${query}" == *"Processed daily notification request"* ]]; then
      echo "Processed daily notification request runId=run-123"
    elif [[ "${query}" == *"Reconciliation completed companyId=mckesson runId=run-123 "* ]]; then
      if [[ "${MOCK_SCENARIO}" != "failed-reconciliation" ]]; then
        echo "Reconciliation completed companyId=mckesson runId=run-123 inactiveCount=0"
      fi
    fi ;;
  *) echo "Unexpected az command" >&2; exit 1 ;;
esac
EOF

cat > "${TEST_DIR}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *"/admin/host/status"* ]]; then
  echo '{"state":"Running"}'
else
  while [[ $# -gt 0 ]]; do
    if [[ "$1" == "-o" ]]; then
      if [[ "${MOCK_SCENARIO}" == "missing-run" ]]; then
        echo '{}' > "$2"
      else
        echo '{"runId":"run-123"}' > "$2"
      fi
      break
    fi
    shift
  done
  printf 200
fi
EOF

cat > "${TEST_DIR}/jq" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  *".state"*) cat > /dev/null; echo Running ;;
  *".runId"*) if [[ "${MOCK_SCENARIO}" != "missing-run" ]]; then echo run-123; fi ;;
  *) cat > /dev/null ;;
esac
EOF
chmod +x "${TEST_DIR}/az" "${TEST_DIR}/curl" "${TEST_DIR}/jq"

export PATH="${TEST_DIR}:${PATH}" RESOURCE_GROUP=test FUNCTION_APP_NAME=test APP_INSIGHTS_APP_ID=test
export SMOKE_TEST_TIMEOUT_SECS=1 SMOKE_TEST_POLL_SECS=1

check() {
  local scenario="$1" expected="$2" message="$3"
  export MOCK_SCENARIO="${scenario}"
  local status=0
  bash "${SCRIPT_DIR}/../smoke_test.sh" > "${TEST_DIR}/output" 2>&1 || status=$?
  if [[ ( "${expected}" == "pass" && "${status}" -ne 0 ) ||
        ( "${expected}" == "fail" && "${status}" -eq 0 ) ]] ||
        ! grep -Fq -- "${message}" "${TEST_DIR}/output"; then
    echo "FAIL: ${scenario}" >&2
    cat "${TEST_DIR}/output" >&2
    exit 1
  fi
  echo "PASS: ${scenario}"
}

check accepted pass "Resend accepted the email"
check wrong-run fail "waiting for the scrape-completed trace"
check processed-only fail "waiting for the email-sent trace"
check failed-reconciliation fail "waiting for successful full reconciliation"
check incomplete fail "smoke scrape was incomplete"
check scrape-error fail "smoke scrape was incomplete"
check daily pass "Resend accepted the email"
check missing-run fail "did not contain a valid run ID"
check disabled fail "RESEND_ENABLED must be true"
export SMOKE_TEST_VERIFY_EMAIL=false
check disabled pass "email check skipped"
