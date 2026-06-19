package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Centralized SQL regex patterns for consistent and robust parsing.
 * Handles whitespace and SQL comments (block and line) to prevent security bypasses.
 */
public class SqlPatterns {
    /** Pattern flags for SQL parsing: case-insensitive and dotall (to match multi-line comments). */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /** Matches optional leading whitespace and/or SQL comments at the start of a statement. */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Unanchored version of PREFIX for use within larger patterns. */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /** Matches mandatory whitespace and/or SQL comments as a separator between keywords. */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";
}
