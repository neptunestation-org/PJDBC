package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transformer that renames identifiers (table names, column names) in SQL.
 *
 * <p>Performs case-insensitive word-boundary replacement of identifiers.
 * Preserves the original case of surrounding SQL.</p>
 *
 * <h2>URL Configuration</h2>
 * <pre>
 * jdbc:filter[rename.OLD_NAME=NEW_NAME]:jdbc:...
 * jdbc:filter[rename.users=customers,rename.created=created_at]:jdbc:...
 * </pre>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Uses regex word boundaries - may not handle all SQL dialects perfectly</li>
 *   <li>Does not parse SQL - works on raw string matching</li>
 *   <li>Quoted identifiers are still matched (no special handling for "name" or [name])</li>
 * </ul>
 *
 * @see FilterDriver
 */
public class RenameTransformer extends AbstractJdbcTransformer {

    private final Map<Pattern, String> replacements = new LinkedHashMap<>();

    /**
     * Create a RenameTransformer with no initial replacements.
     */
    public RenameTransformer() {
    }

    /**
     * Add a rename rule.
     *
     * @param oldName the identifier to find (case-insensitive)
     * @param newName the replacement identifier
     * @return this transformer for chaining
     */
    public RenameTransformer addRename(String oldName, String newName) {
        // Word boundary pattern for case-insensitive matching
        // Matches oldName when surrounded by non-word characters or string boundaries
        Pattern pattern = Pattern.compile(
            "\\b" + Pattern.quote(oldName) + "\\b",
            Pattern.CASE_INSENSITIVE
        );
        replacements.put(pattern, newName);
        return this;
    }

    @Override
    public String transformSql(String sql) throws SQLException {
        if (sql == null || replacements.isEmpty()) {
            return sql;
        }

        String result = sql;
        for (Map.Entry<Pattern, String> entry : replacements.entrySet()) {
            Matcher matcher = entry.getKey().matcher(result);
            result = matcher.replaceAll(entry.getValue());
        }
        return result;
    }
}
