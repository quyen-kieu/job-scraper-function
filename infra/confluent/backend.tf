# Partial backend configuration on purpose: concrete values are supplied per-environment via
# `terraform init -backend-config=dev.backend.hcl` (or prod.backend.hcl), so no environment-specific
# resource names are hardcoded in version control. See infra/confluent/README.md for the exact
# init command and backend setup.
