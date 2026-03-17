package org.pjdbc.sql;

import java.util.regex.Pattern;

/**
 * Centralized SQL patterns for security-hardened parsing.
 */
public class SqlPatterns {

    /**
     * Regex that matches SQL comments (both block and line comments).
     */
    public static final String SQL_COMMENT = "(?:/\\*[\\s\\S]*?\\*/|--.*)";

    /**
     * Regex that matches optional whitespace and comments.
     */
    public static final String SQL_SEP_OPT = "(?:\\s|" + SQL_COMMENT + ")*";

    /**
     * Regex that matches mandatory whitespace and/or comments.
     */
    public static final String SQL_SEP = "(?:\\s|" + SQL_COMMENT + ")+";

    /**
     * Regex for a standard SQL identifier (unquoted).
     */
    public static final String IDENTIFIER_REGEX = "[a-zA-Z_][a-zA-Z0-9_]*";

    private SqlPatterns() {}
}
