package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regex patterns for SQL parsing and security filtering.
 */
public class SqlPatterns {
    /** Regex flags for SQL pattern matching. */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /** Matches optional leading whitespace and comments (block and line). */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Matches a mandatory separator of whitespace or comments. */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    /** Unanchored version of PREFIX for use in larger patterns. */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";
}
