package com.jobscraper.function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class KafkaMessageSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KafkaMessageSupport() {
    }

    static String unwrap(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return rawMessage;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawMessage);

            JsonNode valueNode = root.path("Value");
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                return valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
            }

            JsonNode lowercaseValueNode = root.path("value");
            if (!lowercaseValueNode.isMissingNode() && !lowercaseValueNode.isNull()) {
                return lowercaseValueNode.isTextual() ? lowercaseValueNode.asText() : lowercaseValueNode.toString();
            }

            JsonNode payloadNode = root.path("Payload");
            if (!payloadNode.isMissingNode() && !payloadNode.isNull()) {
                return payloadNode.isTextual() ? payloadNode.asText() : payloadNode.toString();
            }
        } catch (Exception ignored) {
            // Not a Kafka wrapper object; use the raw message as-is.
        }

        return rawMessage;
    }
}

