package com.jobscraper.application.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySentNotificationGuardTest {

    @Test
    void alreadySentReturnsFalseUntilMarkSentIsCalled() {
        InMemorySentNotificationGuard guard = new InMemorySentNotificationGuard();

        assertThat(guard.alreadySent("run-123")).isFalse();

        guard.markSent("run-123");

        assertThat(guard.alreadySent("run-123")).isTrue();
    }

    @Test
    void markSentDoesNotAffectOtherRunIds() {
        InMemorySentNotificationGuard guard = new InMemorySentNotificationGuard();

        guard.markSent("run-123");

        assertThat(guard.alreadySent("run-456")).isFalse();
    }
}

