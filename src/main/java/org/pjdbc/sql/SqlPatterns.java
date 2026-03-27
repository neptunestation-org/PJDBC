package org.pjdbc.sql;

/**
 * Centralized SQL regex patterns for consistent SQL parsing across drivers.
 * Handles SQL comments (block and line) as valid separators.
 */
public class SqlPatterns {
    /**
     * Regex for optional SQL separators (whitespace or comments).
     * Using [^\r\n] for line comments to be safe with Pattern.DOTALL.
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";

    /**
     * Regex for mandatory SQL separators (at least one whitespace or comment).
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    /**
     * Regex for a standard SQL identifier.
     */
    public static final String IDENTIFIER_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";
}
