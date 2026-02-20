package org.pjdbc.sql;

/**
 * Shared regular expression patterns for SQL parsing and transformation.
 */
public class SqlPatterns {
    /**
     * Regular expression that matches SQL separators, including whitespace,
     * block comments, and line comments (-- ...).
     * This pattern uses [\s\S] to match across newlines in block comments.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Optional version of SQL_SEP (matches zero or more separators).
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";
}
