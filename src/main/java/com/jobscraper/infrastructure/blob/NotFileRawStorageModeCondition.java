package com.jobscraper.infrastructure.blob;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when {@code raw-storage.mode} is not {@code file}.
 *
 * <p>Used to prevent {@link NoOpRawScrapeStorage} from competing with
 * {@code com.jobscraper.infrastructure.file.LocalFileRawScrapeStorage} for the single
 * {@link RawScrapeStorage} bean when local-file storage mode is selected.</p>
 */
class NotFileRawStorageModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("raw-storage.mode", "");
        return !"file".equalsIgnoreCase(mode);
    }
}

