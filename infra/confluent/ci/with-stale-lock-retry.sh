#!/usr/bin/env bash
# Shared helper sourced by the Confluent Terraform steps in .github/workflows/deploy.yml.
#
# Why this exists: a previous `plan-confluent` run hung for 30+ minutes (root cause: an earlier,
# now-removed use of `terraform console` to resolve the Kafka REST endpoint — see infra/confluent/
# README.md's "never use terraform console" gotcha). When that run was cancelled, the runner
# process was killed mid-operation, so Terraform's deferred "release the azurerm blob lease" step
# never ran, leaving a STALE state lock behind. Every subsequent `terraform import`/`plan`/`apply`
# in later runs then failed immediately with "Error acquiring the state lock: state blob is
# already locked", even though no run was actually concurrently writing to state.
#
# Per-ref workflow concurrency and sequential jobs do not exclude other refs or local Terraform
# processes sharing the backend. This helper cannot determine whether a lock is stale; coordinate
# all state writers before running it. See infra/confluent/README.md.
#
# Usage: tf_run_with_unlock_retry terraform import -input=false -lock-timeout=120s ADDR ID
tf_run_with_unlock_retry() {
  local output
  local status

  output=$("$@" 2>&1)
  status=$?

  if [ ${status} -ne 0 ] && echo "${output}" | grep -q "Error acquiring the state lock"; then
    local lock_id
    # Terraform's CLI wraps diagnostic output in a box-drawing format, e.g.:
    #   │   ID:        dbfc5ebc-29f0-d56c-b173-981e42e27438
    # A `sed` anchor on "^[[:space:]]*ID:" never matches because the line actually starts with
    # the "│" box-drawing character, not whitespace — so instead match the UUID shape directly,
    # wherever it appears in the output, which is robust to any surrounding prefix/formatting.
    lock_id=$(echo "${output}" | grep -oE '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}' | head -n1)

    if [ -n "${lock_id}" ]; then
      echo "::warning::Detected a Terraform state lock (ID ${lock_id}). It may be held by an active writer; force-unlocking and retrying once: $*"
      terraform force-unlock -force "${lock_id}" 2>&1
      output=$("$@" 2>&1)
      status=$?
    else
      echo "::error::Detected a Terraform state lock but could not parse its lock ID from the output below. Verify the lock is stale before any manual unlock."
    fi
  fi

  echo "${output}"
  return ${status}
}
