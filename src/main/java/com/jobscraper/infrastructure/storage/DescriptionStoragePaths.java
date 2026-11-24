package com.jobscraper.infrastructure.storage;

import com.jobscraper.domain.job.ContentHasher;

import java.util.regex.Pattern;

public final class DescriptionStoragePaths {
    private static final Pattern COMPANY = Pattern.compile("^[a-z0-9][a-z0-9-]{0,49}$");

    private DescriptionStoragePaths() {
    }

    public static String fileName(String externalJobId) {
        return "description-" + ContentHasher.sha256(externalJobId).substring("sha256:".length()) + ".txt";
    }

    public static String fileName(String externalJobId, String content) {
        String legacy = fileName(externalJobId);
        return legacy.substring(0, legacy.length() - ".txt".length()) + "-"
                + ContentHasher.sha256(content).substring("sha256:".length()) + ".txt";
    }

    public static boolean validFileName(String externalJobId, String name) {
        String legacy = fileName(externalJobId);
        String prefix = legacy.substring(0, legacy.length() - ".txt".length());
        return legacy.equals(name) || name.matches(Pattern.quote(prefix) + "-[0-9a-f]{64}\\.txt");
    }

    public static void validateContent(String externalJobId, String name, String content) {
        if (!name.equals(fileName(externalJobId)) && !name.equals(fileName(externalJobId, content))) {
            throw new IllegalStateException("Description content does not match its storage path");
        }
    }

    public static void validateCompany(String companyId) {
        if (companyId == null || !COMPANY.matcher(companyId).matches()) {
            throw new IllegalArgumentException("Invalid company ID for description storage");
        }
    }

    public static void validateRunId(String runId) {
        if (runId == null || !runId.matches("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$")
                || runId.contains("..")) {
            throw new IllegalArgumentException("Invalid run ID for description storage");
        }
    }
}
