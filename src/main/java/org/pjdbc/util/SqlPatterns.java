package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared SQL patterns for consistent security filtering.
 */
public class SqlPatterns {
    /**
     * Flags for all security-related patterns.
     * Pattern.DOTALL allows . to match newlines (essential for multi-line comments).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Matches leading whitespace and SQL comments (both block and line-based).
     * Used at the start of a statement.
     */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Matches whitespace and SQL comments used as a separator between keywords.
     * Requires at least one whitespace or comment.
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";
}
