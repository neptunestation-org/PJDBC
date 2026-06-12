package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regular expression patterns for robust SQL parsing.
 */
public class SqlPatterns {
    /**
     * Regex flags for case-insensitive matching and matching across lines (DOTALL).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Pattern component that matches optional SQL comments and whitespace.
     */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Pattern anchored to the start of a string that matches optional SQL comments and whitespace.
     * Use this to skip leading comments and find the first real SQL keyword.
     */
    public static final String PREFIX = "^" + PREFIX_COMPONENT;

    /**
     * Pattern that matches mandatory SQL separators (at least one whitespace or comment).
     * Use this between keywords instead of \s+.
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";
}
