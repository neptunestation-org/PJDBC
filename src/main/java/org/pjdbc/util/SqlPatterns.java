package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Shared regex patterns for robust SQL parsing.
 */
public class SqlPatterns {

    /**
     * Regex flags for SQL parsing: Case-insensitive and dotall mode (to handle multi-line comments).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Matches leading whitespace and comments at the start of a statement.
     */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Unanchored version of PREFIX, matching zero or more whitespace/comments.
     */
    public static final String PREFIX_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Matches mandatory whitespace or comments between SQL tokens.
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    private SqlPatterns() {} // Utility class
}
