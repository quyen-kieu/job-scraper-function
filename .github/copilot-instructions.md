# GitHub Copilot Instructions for Java Spring Boot & Kafka

## Tech Stack Rules
- Java 21, Spring Boot 4.x, Spring for Apache Kafka.
- Build Tool: Maven.
- Testing: JUnit 5, AssertJ, Mockito, Testcontainers.

## Kafka Security & Quality Guidelines
- Never hardcode bootstrap servers, passwords, or topics. Use `@Value("${spring.kafka...}")` or `@ConfigurationProperties`.
- Always wrap Kafka deserializers in `ErrorHandlingDeserializer` to handle poison pills gracefully.
- Every `@KafkaListener` must specify an explicit `id` or `groupId`, and point to a clear `containerFactory`.
- Prefer record-based payloads over raw strings for strongly-typed messages.

## Code Quality Standards
- No raw types. Use explicit generics.
- Write a corresponding unit or integration test using Testcontainers for any new Kafka Producer or Consumer bean.
