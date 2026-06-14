package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared SQL parsing patterns for PJDBC drivers and transformers.
 * Provides robust regex constants that handle SQL comments and whitespace.
 */
public final class SqlPatterns {
    private SqlPatterns() {}

    /** Regex flags for SQL parsing - enables DOTALL to handle multi-line comments. */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /** Matches optional leading whitespace and/or comments. */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Unanchored version of PREFIX for use within larger patterns. */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Mandatory separator - requires at least one whitespace character or comment. */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";
}
