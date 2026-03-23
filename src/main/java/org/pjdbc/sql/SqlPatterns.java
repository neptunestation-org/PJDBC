package org.pjdbc.sql;

/**
 * Common SQL patterns used for robust SQL parsing across drivers and transformers.
 *
 * <p>Handles SQL comments (line and block) as valid separators to prevent bypasses.</p>
 */
public class SqlPatterns {

    /**
     * Matches SQL comments:
     * - Line comments: -- ...
     * - Block comments: /* ... *\/
     */
    public static final String SQL_COMMENT = "(?:--.*?(?:\r|\n|$)|/\\*[\\s\\S]*?\\*/)";

    /**
     * Matches mandatory SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP = "(?:\\s|" + SQL_COMMENT + ")+";

    /**
     * Matches optional SQL separator (whitespace or comments).
     */
    public static final String SQL_SEP_OPT = "(?:\\s|" + SQL_COMMENT + ")*";

    private SqlPatterns() {
        // utility class
    }
}
