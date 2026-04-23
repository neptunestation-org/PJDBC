package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        String sql = "SELECT * FROM /* comment */ users";
        String transformed = transformer.transformSql(sql);
        // If bypassed, it will NOT contain tenant1.
        assertTrue("SchemaTransformer bypassed! Transformed SQL: " + transformed,
                   transformed.contains("tenant1.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        // modifiable statement check: "^\\s*(SELECT|UPDATE|DELETE)\\b"
        String sql = "/* comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue("WhereTransformer bypassed! Transformed SQL: " + transformed,
                   transformed.contains("tenant_id=1"));
    }

    @Test
    public void testWhereTransformerNoTrailingWhitespace() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "SELECT * FROM users WHERE id=1";
        String transformed = transformer.transformSql(sql);
        // It should append ' AND tenant_id=1'
        assertTrue("WhereTransformer failed with no trailing whitespace! Transformed SQL: " + transformed,
                   transformed.contains("AND tenant_id=1"));
        // It should NOT have two WHEREs
        int whereCount = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\bWHERE\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(transformed);
        while (m.find()) whereCount++;
        assertEquals("Should only have one WHERE clause", 1, whereCount);
    }
}
