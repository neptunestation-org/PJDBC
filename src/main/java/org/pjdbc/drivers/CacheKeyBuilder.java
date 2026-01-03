package org.pjdbc.drivers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for building secure cache keys.
 *
 * Uses SHA-256 instead of Java's hashCode() to prevent hash collision attacks.
 * Java's String.hashCode() is only 32-bit and collisions can be crafted intentionally,
 * enabling cache poisoning attacks where an attacker crafts SQL that collides with
 * legitimate queries.
 */
public final class CacheKeyBuilder {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private CacheKeyBuilder() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate a SHA-256 hash of the input string, returned as hex.
     *
     * @param input the string to hash
     * @return 64-character hex string (256 bits)
     */
    public static String sha256(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Build a cache key with a prefix and SHA-256 hash of the SQL.
     *
     * @param prefix the key prefix (e.g., "pjdbc:cache:")
     * @param sql the SQL query to hash
     * @return the complete cache key
     */
    public static String buildKey(String prefix, String sql) {
        return prefix + sha256(sql);
    }

    /**
     * Build a cache key for a prepared statement with parameters.
     * Combines the SQL template with parameter values to create a unique key.
     *
     * @param prefix the key prefix
     * @param sql the SQL template
     * @param params the parameter values (will be converted to strings)
     * @return the complete cache key
     */
    public static String buildKey(String prefix, String sql, Object... params) {
        StringBuilder sb = new StringBuilder(sql);
        if (params != null && params.length > 0) {
            sb.append("::params::");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(params[i] == null ? "null" : params[i].toString());
            }
        }
        return prefix + sha256(sb.toString());
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }
}
