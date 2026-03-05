package org.pjdbc.sql;

/**
 * Centralized SQL regex patterns for consistent parsing across drivers and transformers.
 */
public class SqlPatterns {
    /**
     * Matches one or more SQL separators: whitespace, block comments, or line comments.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Matches zero or more SQL separators.
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";
}
