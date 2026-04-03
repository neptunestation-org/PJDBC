package org.pjdbc.sql;

/**
 * Common SQL regular expression patterns.
 */
public class SqlPatterns {
    /**
     * Regex for one or more SQL separators (whitespace or comments).
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Regex for zero or more SQL separators (whitespace or comments).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";
}
