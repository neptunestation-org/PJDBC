package org.pjdbc.sql;

/**
 * Shared SQL patterns for robust parsing and transformation.
 *
 * Includes patterns for SQL separators that account for comments.
 */
public final class SqlPatterns {
    private SqlPatterns() {}

    /**
     * Regex for a mandatory SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Regex for an optional SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";
}
