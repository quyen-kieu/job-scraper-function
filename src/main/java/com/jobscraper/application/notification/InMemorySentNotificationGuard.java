package com.jobscraper.application.notification;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SentNotificationGuard}.
 *
 * <p><b>Known limitation:</b> like {@link InMemoryRunCompletionAggregator}, this state lives only
 * in this process's heap and does not survive a host restart. It fully covers the realistic
 * within-process Kafka-redelivery scenario described on {@link SentNotificationGuard}, which is
 * the scenario this project has directly observed during local testing. A durable (Cosmos-backed)
 * guard is a natural Stage 7/8 hardening item for multi-instance deployments.</p>
 */
@Component
public class InMemorySentNotificationGuard implements SentNotificationGuard {

    private final Set<String> sentRunIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public boolean alreadySent(String runId) {
        return sentRunIds.contains(runId);
    }

    @Override
    public void markSent(String runId) {
        sentRunIds.add(runId);
    }
}

