#!/usr/bin/env python3
"""Stage 8, README item 2: "Validate topic names, consumer groups, and configuration templates."

Cross-checks the Kafka topic and consumer-group names declared in
`local.settings.sample.json` (the source of truth for application configuration) against the
names declared in `infra/confluent/main.tf`'s `local.primary_topics` / `local.consumer_groups`
blocks (the source of truth for provisioned infrastructure).

Exits non-zero (failing the CI job) if the two sets ever drift apart — for example, if a topic
name is renamed in one place but not the other. Pure standard library, no extra CI dependency.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SETTINGS_FILE = REPO_ROOT / "local.settings.sample.json"
CONFLUENT_MAIN_TF = REPO_ROOT / "infra" / "confluent" / "main.tf"


def load_app_config() -> tuple[set[str], set[str]]:
    """Extracts the topic and consumer-group names the application itself is configured with."""
    values = json.loads(SETTINGS_FILE.read_text(encoding="utf-8"))["Values"]
    topics = {v for k, v in values.items() if k.endswith("_TOPIC")}
    consumer_groups = {v for k, v in values.items() if k.endswith("_CONSUMER_GROUP")}
    return topics, consumer_groups


def _extract_quoted_values(text: str, block_name: str) -> set[str]:
    """Extracts double-quoted string values from a named `locals` block in main.tf.

    Handles both the `primary_topics = [ "a", "b" ]` list form and the
    `consumer_groups = { key = "value" }` map form by simply grabbing every quoted string
    within the block's braces/brackets, since topic/group names never contain a `"` themselves.
    """
    match = re.search(rf"{re.escape(block_name)}\s*=\s*[\[{{](.*?)[\]}}]", text, re.DOTALL)
    if not match:
        raise ValueError(f"Could not find `{block_name}` block in {CONFLUENT_MAIN_TF}")
    return set(re.findall(r'"([^"]+)"', match.group(1)))


def load_infra_config() -> tuple[set[str], set[str]]:
    """Extracts the topic and consumer-group names provisioned by Terraform."""
    text = CONFLUENT_MAIN_TF.read_text(encoding="utf-8")
    primary_topics = _extract_quoted_values(text, "primary_topics")
    # DLTs are derived as "${t}.DLT" for each primary topic in main.tf; reproduce that here.
    dlt_topics = {f"{t}.DLT" for t in primary_topics}
    consumer_groups = _extract_quoted_values(text, "consumer_groups")
    return primary_topics | dlt_topics, consumer_groups


def main() -> int:
    app_topics, app_groups = load_app_config()
    infra_topics, infra_groups = load_infra_config()

    # The app additionally knows about the 5 DLT topics only implicitly (it never publishes to
    # them directly — Azure's KafkaTrigger/Kafka Streams DLT routing does), so only compare the
    # primary (non-DLT) topic names against the app's explicit KAFKA_*_TOPIC settings.
    infra_primary_topics = {t for t in infra_topics if not t.endswith(".DLT")}

    errors: list[str] = []

    missing_in_infra = app_topics - infra_primary_topics
    if missing_in_infra:
        errors.append(
            "Topics configured in local.settings.sample.json but NOT provisioned in "
            f"infra/confluent/main.tf: {sorted(missing_in_infra)}"
        )

    missing_in_app = infra_primary_topics - app_topics
    if missing_in_app:
        errors.append(
            "Topics provisioned in infra/confluent/main.tf but NOT referenced by any "
            f"KAFKA_*_TOPIC setting in local.settings.sample.json: {sorted(missing_in_app)}"
        )

    missing_group_in_infra = app_groups - infra_groups
    if missing_group_in_infra:
        errors.append(
            "Consumer groups configured in local.settings.sample.json but NOT provisioned "
            f"(no matching ACL) in infra/confluent/main.tf: {sorted(missing_group_in_infra)}"
        )

    missing_group_in_app = infra_groups - app_groups
    if missing_group_in_app:
        errors.append(
            "Consumer groups provisioned in infra/confluent/main.tf but NOT referenced by any "
            f"KAFKA_*_CONSUMER_GROUP setting in local.settings.sample.json: "
            f"{sorted(missing_group_in_app)}"
        )

    if errors:
        print("Kafka configuration drift detected between app config and infrastructure:\n")
        for error in errors:
            print(f"  - {error}")
        print(
            "\nFix: keep local.settings.sample.json's KAFKA_*_TOPIC / KAFKA_*_CONSUMER_GROUP "
            "values and infra/confluent/main.tf's local.primary_topics / local.consumer_groups "
            "in sync."
        )
        return 1

    print(
        f"OK: {len(app_topics)} topics and {len(app_groups)} consumer groups match between "
        "local.settings.sample.json and infra/confluent/main.tf."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

