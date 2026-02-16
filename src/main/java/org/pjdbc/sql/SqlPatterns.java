package org.pjdbc.sql;

/**
 * Common SQL regular expression patterns for secure SQL parsing and transformation.
 *
 * <p>These patterns are designed to be used in security-sensitive drivers like
 * ReadonlyDriver and SchemaValidationDriver to prevent bypasses using SQL comments.</p>
 */
public class SqlPatterns {
    /**
     * Pattern for a SQL separator, which can be one or more whitespace characters,
     * block comments, or line comments.
     *
     * <p>Note: When using this pattern to match block comments spanning multiple lines,
     * the Pattern.DOTALL flag must be used.</p>
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*.*?\\*/|--[^\\r\\n]*)+";

    /**
     * Pattern for the beginning of a SQL statement, matching zero or more
     * leading separators (whitespace or comments).
     *
     * <p>Note: When using this pattern, the Pattern.DOTALL flag must be used
     * to correctly handle multi-line block comments.</p>
     */
    public static final String SQL_PREFIX = "^(?:\\s+|/\\*.*?\\*/|--[^\\r\\n]*)*";

    /**
     * Pattern for an optional SQL separator (zero or more).
     */
    public static final String SQL_OPTIONAL_SEP = "(?:\\s+|/\\*.*?\\*/|--[^\\r\\n]*)*";
}
