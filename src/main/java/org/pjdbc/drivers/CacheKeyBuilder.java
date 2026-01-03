package org.pjdbc.drivers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Utility class for building secure cache keys.
 *
 * Uses SHA-256 instead of Java's hashCode() to prevent hash collision attacks.
 * Java's String.hashCode() is only 32-bit and collisions can be crafted intentionally,
 * enabling cache poisoning attacks where an attacker crafts SQL that collides with
 * legitimate queries.
 *
 * Also supports including connection context (catalog, schema, user) in cache keys
 * to prevent cross-user/cross-schema data leakage in shared caches.
 */
public final class CacheKeyBuilder {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private CacheKeyBuilder() {
        // Utility class - prevent instantiation
    }

    /**
     * Connection context for cache key generation.
     * Prevents cross-user/cross-schema data leakage.
     */
    public static final class ConnectionContext {
        private final String catalog;
        private final String schema;
        private final String user;

        public ConnectionContext(String catalog, String schema, String user) {
            this.catalog = catalog;
            this.schema = schema;
            this.user = user;
        }

        /**
         * Extract context from a JDBC connection.
         * Silently handles any errors by using null values.
         */
        public static ConnectionContext fromConnection(Connection conn) {
            String catalog = null;
            String schema = null;
            String user = null;
            try {
                catalog = conn.getCatalog();
            } catch (SQLException ignored) {}
            try {
                schema = conn.getSchema();
            } catch (SQLException ignored) {}
            try {
                user = conn.getMetaData().getUserName();
            } catch (SQLException ignored) {}
            return new ConnectionContext(catalog, schema, user);
        }

        public String getCatalog() { return catalog; }
        public String getSchema() { return schema; }
        public String getUser() { return user; }

        /**
         * Returns context as a string suitable for inclusion in cache key.
         * Format: "catalog:schema:user" with nulls represented as empty strings.
         */
        public String toKeyString() {
            return (catalog == null ? "" : catalog) + ":" +
                   (schema == null ? "" : schema) + ":" +
                   (user == null ? "" : user);
        }
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
     * Build a cache key including connection context to prevent cross-user/cross-schema leakage.
     *
     * @param prefix the key prefix (e.g., "pjdbc:cache:")
     * @param sql the SQL query to hash
     * @param context the connection context (catalog, schema, user)
     * @return the complete cache key
     */
    public static String buildKey(String prefix, String sql, ConnectionContext context) {
        if (context == null) {
            return buildKey(prefix, sql);
        }
        return prefix + sha256(context.toKeyString() + "::" + sql);
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

    /**
     * Build a cache key for a prepared statement with parameters and connection context.
     *
     * @param prefix the key prefix
     * @param sql the SQL template
     * @param context the connection context (catalog, schema, user)
     * @param params the parameter values (will be converted to strings)
     * @return the complete cache key
     */
    public static String buildKeyWithContext(String prefix, String sql, ConnectionContext context, Object... params) {
        StringBuilder sb = new StringBuilder();
        if (context != null) {
            sb.append(context.toKeyString()).append("::");
        }
        sb.append(sql);
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
