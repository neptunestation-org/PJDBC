package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regular expression patterns for robust SQL parsing.
 * These patterns handle both whitespace and SQL comments (multi-line and single-line).
 */
public class SqlPatterns {
    /** Pattern flags for SQL regex matching: DOTALL to handle multi-line comments, CASE_INSENSITIVE for keywords. */
    public static final int FLAGS = Pattern.DOTALL | Pattern.CASE_INSENSITIVE;

    /**
     * Regex component for SQL comments and whitespace.
     * Matches any amount of whitespace, block comments (/* ... *\/), or line comments (-- ...).
     */
    private static final String COMMENT_WS = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))";

    /**
     * Anchored prefix matching zero or more whitespace or comments at the start of a statement.
     */
    public static final String PREFIX = "^" + COMMENT_WS + "*";

    /**
     * Unanchored version of PREFIX, matching zero or more whitespace or comments.
     */
    public static final String PREFIX_COMPONENT = COMMENT_WS + "*";

    /**
     * Mandatory separator matching one or more whitespace or comments.
     * Used between SQL keywords (e.g., "SELECT" and "FROM").
     */
    public static final String SEP = COMMENT_WS + "+";
}
