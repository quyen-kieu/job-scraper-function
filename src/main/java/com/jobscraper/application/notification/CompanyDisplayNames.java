package com.jobscraper.application.notification;

import java.util.Map;

/**
 * Maps internal company IDs to the human-readable names shown in daily notification emails.
 * Unknown IDs are shown as-is.
 */
final class CompanyDisplayNames {

    private static final Map<String, String> NAMES = Map.of(
            "mckesson", "McKesson",
            "gm-financial", "GM Financial");

    private CompanyDisplayNames() {
    }

    /**
     * Returns the display name for a company ID.
     *
     * @param companyId the internal company ID, may be {@code null}
     * @return the display name, the ID itself if unknown, or {@code null} if the ID is {@code null}
     */
    static String of(String companyId) {
        if (companyId == null) {
            return null;
        }
        return NAMES.getOrDefault(companyId, companyId);
    }
}

