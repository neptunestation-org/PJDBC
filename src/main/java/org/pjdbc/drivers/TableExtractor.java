package org.pjdbc.drivers;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts table names from SQL statements for cache invalidation.
 *
 * This is a lightweight parser that handles common SQL patterns.
 * It's not a full SQL parser but covers typical use cases.
 */
public class TableExtractor {

    // Patterns for different SQL statement types
    private static final Pattern SELECT_FROM = Pattern.compile(
        "\\bFROM\\s+([\\w.`\"\\[\\]]+(?:\\s*,\\s*[\\w.`\"\\[\\]]+)*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JOIN_TABLE = Pattern.compile(
        "\\bJOIN\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INSERT_INTO = Pattern.compile(
        "\\bINSERT\\s+(?:INTO\\s+)?([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UPDATE_TABLE = Pattern.compile(
        "\\bUPDATE\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DELETE_FROM = Pattern.compile(
        "\\bDELETE\\s+FROM\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MERGE_INTO = Pattern.compile(
        "\\bMERGE\\s+INTO\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TRUNCATE_TABLE = Pattern.compile(
        "\\bTRUNCATE\\s+(?:TABLE\\s+)?([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DROP_TABLE = Pattern.compile(
        "\\bDROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "\\bCREATE\\s+(?:TEMP(?:ORARY)?\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALTER_TABLE = Pattern.compile(
        "\\bALTER\\s+TABLE\\s+([\\w.`\"\\[\\]]+)",
        Pattern.CASE_INSENSITIVE
    );

    // Subquery pattern to handle nested queries
    private static final Pattern SUBQUERY = Pattern.compile(
        "\\(\\s*SELECT\\b",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract all table names referenced in a SQL statement.
     *
     * @param sql the SQL statement
     * @return set of table names (normalized to lowercase)
     */
    public static Set<String> extractTables(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> tables = new HashSet<>();

        // Remove string literals to avoid false matches
        String cleanSql = removeStringLiterals(sql);

        // Remove comments
        cleanSql = removeComments(cleanSql);

        // Extract from various patterns
        extractFromPattern(cleanSql, SELECT_FROM, tables);
        extractFromPattern(cleanSql, JOIN_TABLE, tables);
        extractFromPattern(cleanSql, INSERT_INTO, tables);
        extractFromPattern(cleanSql, UPDATE_TABLE, tables);
        extractFromPattern(cleanSql, DELETE_FROM, tables);
        extractFromPattern(cleanSql, MERGE_INTO, tables);
        extractFromPattern(cleanSql, TRUNCATE_TABLE, tables);
        extractFromPattern(cleanSql, DROP_TABLE, tables);
        extractFromPattern(cleanSql, CREATE_TABLE, tables);
        extractFromPattern(cleanSql, ALTER_TABLE, tables);

        return tables;
    }

    /**
     * Extract tables from a SELECT statement (for cache key association).
     */
    public static Set<String> extractTablesFromSelect(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> tables = new HashSet<>();
        String cleanSql = removeStringLiterals(sql);
        cleanSql = removeComments(cleanSql);

        extractFromPattern(cleanSql, SELECT_FROM, tables);
        extractFromPattern(cleanSql, JOIN_TABLE, tables);

        return tables;
    }

    /**
     * Extract tables from a write statement (INSERT/UPDATE/DELETE/etc).
     */
    public static Set<String> extractTablesFromWrite(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> tables = new HashSet<>();
        String cleanSql = removeStringLiterals(sql);
        cleanSql = removeComments(cleanSql);

        extractFromPattern(cleanSql, INSERT_INTO, tables);
        extractFromPattern(cleanSql, UPDATE_TABLE, tables);
        extractFromPattern(cleanSql, DELETE_FROM, tables);
        extractFromPattern(cleanSql, MERGE_INTO, tables);
        extractFromPattern(cleanSql, TRUNCATE_TABLE, tables);
        extractFromPattern(cleanSql, DROP_TABLE, tables);
        extractFromPattern(cleanSql, CREATE_TABLE, tables);
        extractFromPattern(cleanSql, ALTER_TABLE, tables);

        return tables;
    }

    private static void extractFromPattern(String sql, Pattern pattern, Set<String> tables) {
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String tableList = matcher.group(1);
            // Handle comma-separated table lists (e.g., FROM a, b, c)
            for (String table : tableList.split(",")) {
                String normalized = normalizeTableName(table.trim());
                if (!normalized.isEmpty()) {
                    tables.add(normalized);
                }
            }
        }
    }

    /**
     * Normalize a table name by removing quotes, backticks, brackets, and schema prefixes.
     * Converts to lowercase for case-insensitive matching.
     */
    static String normalizeTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return "";
        }

        // Remove quotes, backticks, and brackets
        String normalized = tableName
            .replace("`", "")
            .replace("\"", "")
            .replace("[", "")
            .replace("]", "")
            .trim();

        // Remove alias (e.g., "users u" -> "users")
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            normalized = normalized.substring(0, spaceIndex);
        }

        // Remove schema prefix if present (e.g., "schema.table" -> "table")
        // But keep the full name for accurate matching
        // Actually, let's keep the schema for better accuracy

        // Handle AS keyword (e.g., "users AS u")
        int asIndex = normalized.toUpperCase().indexOf(" AS ");
        if (asIndex > 0) {
            normalized = normalized.substring(0, asIndex);
        }

        return normalized.toLowerCase();
    }

    /**
     * Remove string literals from SQL to avoid false pattern matches.
     */
    private static String removeStringLiterals(String sql) {
        // Replace 'string' and "string" with empty strings
        StringBuilder result = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (escaped) {
                escaped = false;
                if (!inSingleQuote && !inDoubleQuote) {
                    result.append(c);
                }
                continue;
            }

            if (c == '\\') {
                escaped = true;
                if (!inSingleQuote && !inDoubleQuote) {
                    result.append(c);
                }
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                // In SQL, double quotes are for identifiers, not strings
                // But some databases use them for strings
                // For safety, we'll keep identifiers but skip if we detect a string pattern
                result.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Remove SQL comments (both -- and /* styles).
     */
    private static String removeComments(String sql) {
        // Remove -- comments
        StringBuilder result = new StringBuilder();
        String[] lines = sql.split("\n");
        for (String line : lines) {
            int commentIndex = line.indexOf("--");
            if (commentIndex >= 0) {
                result.append(line.substring(0, commentIndex));
            } else {
                result.append(line);
            }
            result.append(" ");
        }

        // Remove /* */ comments
        String s = result.toString();
        StringBuilder finalResult = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i < s.length() - 1 && s.charAt(i) == '/' && s.charAt(i + 1) == '*') {
                // Find closing */
                int end = s.indexOf("*/", i + 2);
                if (end >= 0) {
                    i = end + 2;
                } else {
                    break; // Unclosed comment, stop processing
                }
            } else {
                finalResult.append(s.charAt(i));
                i++;
            }
        }

        return finalResult.toString();
    }
}
