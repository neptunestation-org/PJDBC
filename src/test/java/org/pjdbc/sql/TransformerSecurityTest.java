package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Traditional case
        assertEquals("SELECT * FROM tenant1.users", transformer.transformSql("SELECT * FROM users"));

        // Comment bypass case
        String sql = "SELECT * FROM/*comment*/users";
        String transformed = transformer.transformSql(sql);
        assertEquals("SELECT * FROM/*comment*/tenant1.users", transformed);
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Leading comment bypass
        String sql = "/* comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected WHERE clause in transformed SQL: " + transformed, transformed.contains("WHERE tenant_id=1"));
        assertTrue("Expected comment preserved: " + transformed, transformed.startsWith("/* comment */"));
    }

    @Test
    public void testWhereTransformerInsertionPointWithComment() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Comment before ORDER BY
        String sql = "SELECT * FROM users /* comment */ ORDER BY name";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected WHERE before ORDER BY: " + transformed, transformed.contains("WHERE tenant_id=1 /* comment */ ORDER BY"));
    }
}
