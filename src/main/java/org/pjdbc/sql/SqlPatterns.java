package org.pjdbc.sql;

/**
 * SQL patterns for parsing and transformation.
 */
public class SqlPatterns {
    /**
     * Regex pattern for a SQL separator (whitespace, block comments, or line comments).
     * Must match at least one separator.
     */
    public static final String SQL_SEP = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--.*?(?:\\r\\n|\\r|\\n|$))+";

    /**
     * Optional version of SQL_SEP.
     */
    public static final String SQL_SEP_OPT = "(?:\\s+|/\\*[\\s\\S]*?\\*/|--.*?(?:\\r\\n|\\r|\\n|$))*";
}
