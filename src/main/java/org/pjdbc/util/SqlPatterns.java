package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Centralized regex patterns for SQL parsing across PJDBC drivers.
 * Handles SQL comments and whitespace consistently to prevent security bypasses.
 */
public final class SqlPatterns {
    /**
     * Regex for a single SQL separator (whitespace, block comment, or line comment).
     */
    private static final String SEP_COMPONENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))";

    /**
     * Pattern component for optional leading whitespace and comments.
     */
    public static final String PREFIX_COMPONENT = SEP_COMPONENT + "*";

    /**
     * Anchored prefix pattern for the start of a statement.
     */
    public static final String PREFIX = "^" + PREFIX_COMPONENT;

    /**
     * Mandatory separator between keywords or identifiers.
     */
    public static final String SEP = SEP_COMPONENT + "+";

    /**
     * Default regex flags for SQL parsing: case-insensitive and dot-matches-all.
     * DOTALL is critical for matching multi-line block comments.
     */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private SqlPatterns() {
        // Utility class
    }
}
