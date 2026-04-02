package org.pjdbc.sql;

/**
 * Centralized SQL patterns for robust parsing.
 * Handles whitespace and SQL comments (block and line).
 */
public class SqlPatterns {
    /**
     * Regex for optional SQL separator: whitespace, block comments, or line comments.
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)*";

    /**
     * Regex for mandatory SQL separator: at least one whitespace, block comment, or line comment.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*)+";

    private SqlPatterns() {}
}
