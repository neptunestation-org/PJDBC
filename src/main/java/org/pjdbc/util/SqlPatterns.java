package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Common SQL patterns used for security filtering and transformation.
 */
public class SqlPatterns {
    /**
     * Flags for SQL regex matching.
     * Includes Pattern.DOTALL to allow matching across multiple lines (important for comments).
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /**
     * Regex component for optional SQL comments and whitespace at the start of a statement.
     */
    public static final String PREFIX = "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*";

    /**
     * Regex component for mandatory separator (whitespace or comments).
     */
    public static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    private SqlPatterns() {}
}
