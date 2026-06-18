package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Centralized utility for SQL parsing regex patterns.
 */
public class SqlPatterns {
    /** Pattern matching any combination of SQL whitespace and comments at the start of a string. */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Unanchored version of PREFIX, matching zero or more comments/whitespace. */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Mandatory separator requiring at least one whitespace or comment. */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    /** Common flags for SQL regex patterns. */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private SqlPatterns() {}
}
