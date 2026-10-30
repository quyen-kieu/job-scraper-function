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
# It is SAFE to auto-force-unlock on that specific error in this pipeline (and only this
# pipeline), because:
#   - the workflow's top-level `concurrency: { group: deploy-${{ github.ref }} }` already
#     guarantees only one workflow run per ref is active at a time, and
#   - within a single run, every job that touches this Terraform state (plan-confluent,
#     apply-confluent) executes strictly sequentially via `needs:` — never in parallel.
# So a lock error here can only mean a stale lease from an earlier crashed/cancelled run, never a
# real concurrent writer.
#
# Usage: tf_run_with_unlock_retry terraform import -input=false -lock-timeout=120s ADDR ID
tf_run_with_unlock_retry() {
  local output
  local status

  output=$("$@" 2>&1)
  status=$?

  if [ ${status} -ne 0 ] && echo "${output}" | grep -q "Error acquiring the state lock"; then
    local lock_id
    lock_id=$(echo "${output}" | sed -n 's/^[[:space:]]*ID:[[:space:]]*//p' | head -n1 | tr -d '[:space:]\r')

    if [ -n "${lock_id}" ]; then
      echo "::warning::Detected a stale Terraform state lock (ID ${lock_id}), likely left behind by a previous cancelled/crashed run. Force-unlocking and retrying once: $*"
      terraform force-unlock -force "${lock_id}" 2>&1
      output=$("$@" 2>&1)
      status=$?
    else
      echo "::error::Detected a stale Terraform state lock but could not parse its lock ID from the output below. A manual 'terraform force-unlock <id>' may be required."
    fi
  fi

  echo "${output}"
  return ${status}
}

