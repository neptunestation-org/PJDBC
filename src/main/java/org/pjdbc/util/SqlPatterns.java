package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regular expression components for secure SQL parsing.
 * These patterns handle SQL comments and multi-line statements.
 */
public class SqlPatterns {

    /**
     * Regex for SQL comments: block comments /* ... *\/ and line comments -- ...
     */
    private static final String COMMENT_REGEX = "(?:/\\*.*?\\*/|--.*?(?:\\n|$))";

    /**
     * Optional whitespace and/or comments.
     */
    private static final String WHITESPACE_AND_COMMENTS = "(?:\\s|" + COMMENT_REGEX + ")*";

    /**
     * Mandatory separator: at least one whitespace character or comment.
     */
    public static final String SEP = "(?:\\s|" + COMMENT_REGEX + ")+";

    /**
     * Anchored start for a SQL statement, allowing leading whitespace and comments.
     */
    public static final String PREFIX = "^" + WHITESPACE_AND_COMMENTS;

    /**
     * Common regex flags for SQL parsing: case-insensitive and dotall (to handle multi-line comments).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Regex for a standard SQL identifier (unquoted).
     */
    public static final String IDENTIFIER_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";

    private SqlPatterns() {}
}
