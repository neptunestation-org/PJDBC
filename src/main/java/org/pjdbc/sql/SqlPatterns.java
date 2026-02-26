package org.pjdbc.sql;

/**
 * Centralized SQL patterns for robust parsing and transformation.
 */
public class SqlPatterns {

    /**
     * Regex for a mandatory SQL separator.
     * Matches one or more occurrences of whitespace, block comments, or line comments.
     * Uses non-capturing groups to maintain stable capturing group indices in complex patterns.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Regex for an optional SQL separator.
     * Matches zero or more occurrences of whitespace or comments.
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";

    private SqlPatterns() {
        // Utility class
    }
}
