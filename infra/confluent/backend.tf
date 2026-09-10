# Partial backend configuration on purpose: concrete values are supplied per-environment via
# `terraform init -backend-config=dev.backend.hcl` (or prod.backend.hcl), so no environment-specific
# resource names are hardcoded in version control. See infra/confluent/README.md for the exact
# init command and docs/STAGE7_PLAN.md open question #2 for the storage account decision
# (a dedicated "job-scraper-tfstate" storage account, container "confluent", one blob key per env).

