package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transformer that adds a schema prefix to table names in SQL.
 *
 * <p>Prefixes unqualified table names after FROM, JOIN, INTO, UPDATE, and TABLE keywords.
 * Already-qualified names (containing a dot) are not modified.</p>
 *
 * <h2>URL Configuration</h2>
 * <pre>
 * jdbc:filter[schema=tenant_123]:jdbc:postgresql://localhost/db
 * </pre>
 *
 * <h2>Examples</h2>
 * <ul>
 *   <li>{@code SELECT * FROM users} → {@code SELECT * FROM tenant_123.users}</li>
 *   <li>{@code SELECT * FROM public.users} → unchanged (already qualified)</li>
 *   <li>{@code INSERT INTO orders} → {@code INSERT INTO tenant_123.orders}</li>
 *   <li>{@code UPDATE customers SET} → {@code UPDATE tenant_123.customers SET}</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Uses regex heuristics - may not handle all SQL dialects perfectly</li>
 *   <li>Does not parse SQL AST</li>
 *   <li>May prefix non-table identifiers if they follow table keywords</li>
 * </ul>
 *
 * @see FilterDriver
 */
public class SchemaTransformer extends AbstractJdbcTransformer {

    private final String schemaPrefix;

    // Pattern to match table name contexts:
    // - FROM tablename
    // - JOIN tablename
    // - INTO tablename
    // - UPDATE tablename
    // - TABLE tablename (for TRUNCATE TABLE, etc.)
    // Captures: keyword, separator (whitespace/comments), table name (not already qualified)
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "\\b(FROM|JOIN|INTO|UPDATE|TABLE)((?:\\s|/\\*.*?\\*/|--.*?(?:\\n|$))+)(?!\\w+\\.)([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Create a SchemaTransformer with the specified schema prefix.
     *
     * @param schema the schema to prefix table names with
     */
    public SchemaTransformer(String schema) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("Schema prefix cannot be null or empty");
        }
        this.schemaPrefix = schema + ".";
    }

    @Override
    public String transformSql(String sql) throws SQLException {
        if (sql == null) {
            return sql;
        }

        Matcher matcher = TABLE_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String keyword = matcher.group(1);
            String separator = matcher.group(2);
            String tableName = matcher.group(3);
            // Replace with: keyword + original separator + schema.tablename
            // We use Matcher.quoteReplacement for schemaPrefix + tableName to be safe
            matcher.appendReplacement(result, keyword + Matcher.quoteReplacement(separator) + Matcher.quoteReplacement(schemaPrefix + tableName));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
