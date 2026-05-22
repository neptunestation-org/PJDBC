package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transformer that appends conditions to WHERE clauses in SQL.
 *
 * <p>Adds a condition to SELECT, UPDATE, and DELETE statements. If a WHERE clause
 * exists, the condition is appended with AND. If no WHERE clause exists, one is
 * added before any GROUP BY, HAVING, ORDER BY, LIMIT, or end of statement.</p>
 *
 * <h2>URL Configuration</h2>
 * <pre>
 * jdbc:filter[where=deleted=false]:jdbc:postgresql://localhost/db
 * jdbc:filter[where=tenant_id=123]:jdbc:mysql://localhost/db
 * </pre>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>{@code SELECT * FROM users} → {@code SELECT * FROM users WHERE deleted=false}</li>
 *   <li>{@code SELECT * FROM users WHERE active=true} → {@code SELECT * FROM users WHERE active=true AND deleted=false}</li>
 *   <li>{@code SELECT * FROM users ORDER BY name} → {@code SELECT * FROM users WHERE deleted=false ORDER BY name}</li>
 *   <li>{@code UPDATE users SET x=1} → {@code UPDATE users SET x=1 WHERE deleted=false}</li>
 *   <li>{@code DELETE FROM users} → {@code DELETE FROM users WHERE deleted=false}</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Uses regex heuristics - may not handle complex SQL perfectly</li>
 *   <li>Does not parse SQL AST</li>
 *   <li>INSERT statements are not modified</li>
 *   <li>Subqueries may not be handled correctly in all cases</li>
 * </ul>
 *
 * @see FilterDriver
 */
public class WhereTransformer extends AbstractJdbcTransformer {

    private final String condition;

    // Separator pattern that matches whitespace and SQL comments
    private static final String SEP = "(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+";

    // Pattern to detect if statement already has WHERE
    private static final Pattern HAS_WHERE = Pattern.compile(
        "\\bWHERE\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to find insertion point for new WHERE clause
    // Matches: GROUP BY, HAVING, ORDER BY, LIMIT, OFFSET, UNION, INTERSECT, EXCEPT, or end
    private static final Pattern WHERE_INSERTION_POINT = Pattern.compile(
        SEP + "(GROUP" + SEP + "BY|HAVING|ORDER" + SEP + "BY|LIMIT|OFFSET|UNION|INTERSECT|EXCEPT|FOR" + SEP + "UPDATE|FOR" + SEP + "SHARE)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to detect SELECT/UPDATE/DELETE statements (not INSERT)
    private static final Pattern MODIFIABLE_STATEMENT = Pattern.compile(
        "^(?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))*(SELECT|UPDATE|DELETE)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to find the last WHERE for appending AND
    // We need to find WHERE that's not inside parentheses (subquery)
    // This is a simplification - we find WHERE and append at the insertion point
    private static final Pattern WHERE_CLAUSE_END = Pattern.compile(
        "\\bWHERE\\b(.+?)(?=" + SEP + "(?:GROUP" + SEP + "BY|HAVING|ORDER" + SEP + "BY|LIMIT|OFFSET|UNION|INTERSECT|EXCEPT|FOR" + SEP + "UPDATE|FOR" + SEP + "SHARE)|$)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Create a WhereTransformer with the specified condition.
     *
     * @param condition the condition to append (e.g., "deleted=false")
     */
    public WhereTransformer(String condition) {
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("WHERE condition cannot be null or empty");
        }
        this.condition = condition;
    }

    @Override
    public String transformSql(String sql) throws SQLException {
        if (sql == null) {
            return sql;
        }

        // Only modify SELECT, UPDATE, DELETE statements
        if (!MODIFIABLE_STATEMENT.matcher(sql).find()) {
            return sql;
        }

        // Check if WHERE already exists
        if (HAS_WHERE.matcher(sql).find()) {
            return appendToExistingWhere(sql);
        } else {
            return insertNewWhere(sql);
        }
    }

    /**
     * Append condition to existing WHERE clause with AND.
     */
    private String appendToExistingWhere(String sql) {
        Matcher matcher = WHERE_CLAUSE_END.matcher(sql);
        if (matcher.find()) {
            // Find where the WHERE clause content ends
            int whereStart = matcher.start();
            int contentEnd = matcher.end(1);

            // Insert AND condition at the end of WHERE content
            String beforeCondition = sql.substring(0, contentEnd);
            String afterCondition = sql.substring(contentEnd);

            // Trim trailing whitespace from WHERE content, add AND, then restore
            String trimmed = beforeCondition.stripTrailing();
            return trimmed + " AND " + condition + afterCondition;
        }

        // Fallback: append at end
        return sql.stripTrailing() + " AND " + condition;
    }

    /**
     * Insert new WHERE clause at appropriate position.
     */
    private String insertNewWhere(String sql) {
        Matcher matcher = WHERE_INSERTION_POINT.matcher(sql);
        if (matcher.find()) {
            // Insert WHERE before GROUP BY, ORDER BY, etc.
            int insertPos = matcher.start();
            return sql.substring(0, insertPos) + " WHERE " + condition + sql.substring(insertPos);
        }

        // No insertion point found - append at end
        return sql.stripTrailing() + " WHERE " + condition;
    }
}
