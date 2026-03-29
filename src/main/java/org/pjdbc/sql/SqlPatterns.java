package org.pjdbc.sql;

/**
 * Common SQL patterns for PJDBC drivers and transformers.
 *
 * <p>Centralizes comment-aware SQL separator regexes to prevent security bypasses
 * that use SQL comments instead of whitespace.</p>
 */
public class SqlPatterns {

    /**
     * Required SQL separator: whitespace, block comments, or line comments (one or more).
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Optional SQL separator: whitespace, block comments, or line comments (zero or more).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";

    private SqlPatterns() {
        // utility class
    }
}
