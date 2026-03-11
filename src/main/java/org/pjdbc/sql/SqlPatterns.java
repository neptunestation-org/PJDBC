package org.pjdbc.sql;

/**
 * Common SQL regex patterns for PJDBC drivers and transformers.
 *
 * <p>Centralizes patterns for SQL comments and whitespace to ensure consistent
 * and secure parsing of SQL statements, preventing bypasses that use
 * comments instead of whitespace.</p>
 */
public final class SqlPatterns {

    /**
     * Regex for SQL comments:
     * - Block comments: /* ... * / (non-greedy)
     * - Line comments: -- ...
     */
    public static final String SQL_COMMENT = "(?:/\\*[\\s\\S]*?\\*/|--.*)";

    /**
     * Regex for mandatory SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP = "(?:\\s|" + SQL_COMMENT + ")+";

    /**
     * Regex for optional SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP_OPT = "(?:\\s|" + SQL_COMMENT + ")*";

    private SqlPatterns() {
        // Utility class
    }
}
