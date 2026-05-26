package org.pjdbc.util;

/**
 * Shared regular expression patterns for SQL parsing and security filtering.
 */
public class SqlPatterns {
    /**
     * Pattern for a SQL prefix (leading whitespace or comments).
     * Supports both block comments (/*...*\/) and line comments (--...).
     */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Component of a SQL prefix without the start-of-string anchor.
     */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Pattern for a SQL separator (interspersed whitespace or comments).
     * Requires at least one whitespace or comment.
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    private SqlPatterns() {}
}
