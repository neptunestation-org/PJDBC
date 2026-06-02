package org.pjdbc.util;

import java.util.regex.Pattern;

/**
 * Unified SQL parsing patterns for PJDBC drivers and transformers.
 *
 * <p>These patterns provide a robust way to handle SQL comments and whitespace,
 * preventing security bypasses where attackers use comments to hide DML/DDL keywords
 * from regex-based filters.</p>
 */
public class SqlPatterns {
    /** Pattern flags used for SQL parsing: Case-insensitive and DOTALL (to match newlines in comments). */
    public static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    /** Non-capturing group that matches a single whitespace character or an SQL comment. */
    private static final String WS_OR_COMMENT = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))";

    /** Regex component that matches zero or more whitespace characters or SQL comments at the start of a string. */
    public static final String PREFIX = "^" + WS_OR_COMMENT + "*";

    /** Regex component that matches zero or more whitespace characters or SQL comments (unanchored). */
    public static final String PREFIX_COMPONENT = WS_OR_COMMENT + "*";

    /** Regex component that matches one or more whitespace characters or SQL comments (mandatory separator). */
    public static final String SEP = WS_OR_COMMENT + "+";
}
