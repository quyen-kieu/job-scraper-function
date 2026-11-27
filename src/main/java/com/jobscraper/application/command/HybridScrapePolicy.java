package com.jobscraper.application.command;

import com.jobscraper.domain.job.NormalizedJobPosting;
import com.jobscraper.infrastructure.cosmos.JobPostingRepository;
import com.jobscraper.scraper.api.ScrapeContext;
import com.jobscraper.scraper.api.ScrapedJob;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class HybridScrapePolicy {
    private static final long FULL_REFRESH_DAYS = 7;
    private final JobPostingRepository repository;

    public HybridScrapePolicy(JobPostingRepository repository) {
        this.repository = repository;
    }

    public ScrapeContext createContext(ScrapeCompanyCommand command, Instant now) {
        boolean fullRefresh = repository.lastSuccessfulReconciliationAt(command.companyId())
                .map(last -> ChronoUnit.DAYS.between(last.atZone(ZoneOffset.UTC).toLocalDate(),
                        now.atZone(ZoneOffset.UTC).toLocalDate()) >= FULL_REFRESH_DAYS)
                .orElse(true);
        return new ScrapeContext(command.runId(), command.companyId(), command.requestedAt(), fullRefresh,
                (job, reliableDate) -> needsDetail(command.companyId(), job, reliableDate));
    }

    private boolean needsDetail(String companyId, ScrapedJob job, boolean reliableDate) {
        return repository.findById(NormalizedJobPosting.buildId(companyId, job.externalJobId()), companyId)
                .map(existing -> !existing.isActive()
                        || (isBlank(existing.descriptionPlainText()) && isBlank(existing.description()))
                        || !Objects.equals(existing.titleRaw(), job.title())
                        || !Objects.equals(existing.location(), job.location())
                        || !Objects.equals(existing.url(), job.url())
                        || (reliableDate && job.datePosted() != null
                            && !Objects.equals(existing.datePosted(), job.datePosted())))
                .orElse(true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
