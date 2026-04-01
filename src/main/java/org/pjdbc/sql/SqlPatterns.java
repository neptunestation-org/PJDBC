package org.pjdbc.sql;

/**
 * Centralized regex patterns for SQL parsing in PJDBC.
 * Handles whitespace and SQL comments (block and line) as valid separators.
 */
public class SqlPatterns {
    /**
     * Regex for a sequence of one or more SQL separators.
     * Includes whitespace and comments.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Regex for an optional sequence of SQL separators (zero or more).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";
}
