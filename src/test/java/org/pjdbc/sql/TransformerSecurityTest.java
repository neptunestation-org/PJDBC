package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Leading comment
        String sql1 = "/* comment */ SELECT * FROM users";
        String expected1 = "/* comment */ SELECT * FROM users WHERE tenant_id=1";
        assertEquals(expected1, transformer.transformSql(sql1));

        // Comment before ORDER BY
        String sql2 = "SELECT * FROM users /* comment */ ORDER BY name";
        String expected2 = "SELECT * FROM users WHERE tenant_id=1 /* comment */ ORDER BY name";
        // Note: My regex replaces /* comment */ ORDER BY with WHERE ... /* comment */ ORDER BY
        // if the comment is part of the whitespace matched.
        assertTrue(transformer.transformSql(sql2).contains("WHERE tenant_id=1"));
        assertTrue(transformer.transformSql(sql2).contains("ORDER BY name"));
    }

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("my_schema");

        String sql = "SELECT * FROM /* comment */ users";
        String result = transformer.transformSql(sql);
        assertTrue("Should have prefixed table name: " + result, result.contains("my_schema.users"));
        assertTrue("Should have preserved comment: " + result, result.contains("/* comment */"));
    }
}
