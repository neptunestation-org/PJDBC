package org.pjdbc.sql;

/**
 * Centralized SQL patterns for consistent parsing and security.
 */
public class SqlPatterns {
    /**
     * Regex for a SQL separator (whitespace or comment).
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--.*)+";

    /**
     * Optional SQL separator (zero or more whitespace or comments).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--.*)*";
}
