#!/usr/bin/env bash
# Registers every Confluent identifier this pipeline can print as a GitHub Actions log mask
# (::add-mask::), so it is rendered as *** in the job log.
#
# Why: this repository is public, so workflow logs are publicly readable. None of these values is
# a secret on its own, but together they are reconnaissance data: the environment/cluster IDs and
# REST/bootstrap hostname identify the cluster, the service-account IDs are ACL principals, and an
# API key ID is the SASL *username* half of a Kafka credential. Terraform plan/apply output and
# the import steps print all of them.
#
# Masks are per job, so source/run this as the FIRST step after checkout in every job that talks
# to Confluent. Values printed before a mask is registered are not retroactively hidden.
#
# Troubleshooting is preserved: logs still show the human-readable resource addresses and display
# names (e.g. sa-job-scraper-normalizer); look the IDs up in the Confluent Cloud console or via
# `confluent iam service-account list` when needed.
#
# Required env: CONFLUENT_CLOUD_API_KEY, CONFLUENT_CLOUD_API_SECRET,
#               CONFLUENT_ENVIRONMENT_ID, CONFLUENT_CLUSTER_ID
# Never fails the job: a masking problem is reported as a warning.

set -uo pipefail

mask() {
  local value="$1"
  if [ -n "${value}" ] && [ "${value}" != "null" ]; then
    echo "::add-mask::${value}"
  fi
}

mask "${CONFLUENT_ENVIRONMENT_ID:-}"
mask "${CONFLUENT_CLUSTER_ID:-}"

if [ -z "${CONFLUENT_CLOUD_API_KEY:-}" ] || [ -z "${CONFLUENT_CLOUD_API_SECRET:-}" ]; then
  echo "::warning::Confluent Cloud API credentials not available; only environment/cluster IDs were masked."
  exit 0
fi

auth="${CONFLUENT_CLOUD_API_KEY}:${CONFLUENT_CLOUD_API_SECRET}"
api="https://api.confluent.cloud"

# Cluster endpoints (REST endpoint is printed by the topic-import step; bootstrap is a secret
# elsewhere in this pipeline). Mask both the full URL/host:port and the bare hostname.
if cluster_json=$(curl -sf --max-time 30 -u "${auth}" \
    "${api}/cmk/v2/clusters/${CONFLUENT_CLUSTER_ID:-}?environment=${CONFLUENT_ENVIRONMENT_ID:-}"); then
  for endpoint in $(echo "${cluster_json}" | jq -r '.spec.http_endpoint // empty, .spec.kafka_bootstrap_endpoint // empty'); do
    mask "${endpoint}"
    host="${endpoint#*://}"
    host="${host%%/*}"
    mask "${host}"
    mask "${host%%:*}"
  done
else
  echo "::warning::Could not read Kafka cluster details for masking."
fi

# Service accounts owned by this project, and the API keys they own.
if ! sa_json=$(curl -sf --max-time 30 -u "${auth}" "${api}/iam/v2/service-accounts?page_size=100"); then
  echo "::warning::Could not list Confluent service accounts for masking."
  exit 0
fi

count=0
for sa_id in $(echo "${sa_json}" | jq -r '.data[]? | select(.display_name | startswith("sa-job-scraper-")) | .id'); do
  mask "${sa_id}"
  count=$((count + 1))
  if key_json=$(curl -sf --max-time 30 -u "${auth}" "${api}/iam/v2/api-keys?spec.owner=${sa_id}&page_size=100"); then
    for key_id in $(echo "${key_json}" | jq -r '.data[]?.id'); do
      mask "${key_id}"
    done
  fi
done

echo "Registered log masks for Confluent environment/cluster, endpoints, ${count} service account(s) and their API key IDs."

