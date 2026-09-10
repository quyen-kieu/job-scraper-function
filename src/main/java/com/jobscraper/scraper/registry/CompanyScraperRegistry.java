package com.jobscraper.scraper.registry;

import com.jobscraper.scraper.api.CompanyScraper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for all pluggable {@link CompanyScraper} implementations.
 *
 * <p>On construction, collects all Spring-managed {@code CompanyScraper} beans and indexes
 * them by {@link CompanyScraper#companyId()} for fast lookup. This approach avoids
 * if-statements or switch logic in the consumer.</p>
 */
@Component
public class CompanyScraperRegistry {

    private final Map<String, CompanyScraper> scrapersByCompanyId;

    public CompanyScraperRegistry(List<CompanyScraper> scrapers) {
        this.scrapersByCompanyId = scrapers.stream()
                .collect(Collectors.toUnmodifiableMap(CompanyScraper::companyId, Function.identity()));
    }

    /**
     * Looks up a scraper for the given company ID.
     *
     * @param companyId the company to look up
     * @return {@code Optional} containing the scraper, or empty if not found
     */
    public Optional<CompanyScraper> find(String companyId) {
        return Optional.ofNullable(scrapersByCompanyId.get(companyId));
    }
}

