package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regex patterns for SQL parsing and security filtering.
 */
public class SqlPatterns {

    /**
     * Flags used for SQL regex matching.
     * Pattern.DOTALL allows . to match newlines, which is essential for multi-line comments.
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Matches optional SQL comments (both /* ... *\/ and -- ...) and whitespace.
     */
    private static final String OPTIONAL_STUFF = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Matches mandatory SQL separator (at least one whitespace or comment).
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    /**
     * Pattern for the beginning of a SQL statement, allowing leading comments and whitespace.
     */
    public static final String PREFIX = "^" + OPTIONAL_STUFF;

    /**
     * Pattern component for matching comments/whitespace within a statement (e.g., after 'AS (').
     */
    public static final String PREFIX_COMPONENT = OPTIONAL_STUFF;

    private SqlPatterns() {
        // Utility class
    }
}
