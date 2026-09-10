package com.jobscraper.domain.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Computes stable content hashes for job posting change detection.
 *
 * <p>Used to populate {@link NormalizedJobPosting#contentHash()} so that repeated scrapes
 * of an unchanged posting can be distinguished from genuine content changes.</p>
 */
public final class ContentHasher {

    private static final String ALGORITHM = "SHA-256";

    private ContentHasher() {
        // Non-instantiable utility class
    }

    /**
     * Computes a SHA-256 hash of the given value.
     *
     * @param value the content to hash
     * @return the hash, prefixed with {@code "sha256:"} per the README's
     *         {@code job-postings} document example (e.g. {@code "sha256:..."})
     */
    public static String sha256(String value) {
        Objects.requireNonNull(value, "value must not be null");

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS/JCA guarantee); this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

