package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared SQL regex patterns for security filtering.
 */
public class SqlPatterns {
    /**
     * Regex flags for SQL parsing: Case-insensitive and dot matches all (for multi-line comments).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Regex component that matches optional SQL comments and whitespace.
     */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Regex that matches the start of a statement, skipping leading comments and whitespace.
     */
    public static final String PREFIX = "^" + PREFIX_COMPONENT;

    /**
     * Regex that matches mandatory SQL separator (at least one whitespace or comment).
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";
}
