package org.pjdbc.sql;

import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Unqualified table with comments between FROM and table name
        String sql = "SELECT * FROM /* comment */ users";
        String transformed = transformer.transformSql(sql);

        assertTrue("Transformed SQL should contain prefixed table name, got: " + transformed,
                   transformed.contains("tenant1.users"));
        assertTrue("Original separator (comments) should be preserved, got: " + transformed,
                   transformed.contains("/* comment */"));
    }

    @Test
    public void testWhereTransformerModifiableBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // SELECT with leading comments
        String sql = "/* comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);

        assertTrue("Transformed SQL should contain WHERE clause, got: " + transformed,
                   transformed.contains("WHERE tenant_id=123"));
    }

    @Test
    public void testWhereTransformerInsertionPointBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // ORDER BY with comments before it
        String sql = "SELECT * FROM users /* comment */ ORDER BY name";
        String transformed = transformer.transformSql(sql);

        assertTrue("Transformed SQL should contain WHERE clause before ORDER BY, got: " + transformed,
                   transformed.contains("WHERE tenant_id=123 /* comment */ ORDER BY name"));
    }

    @Test
    public void testWhereTransformerAppendBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // Existing WHERE with comments after it before ORDER BY
        String sql = "SELECT * FROM users WHERE active=true /* comment */ ORDER BY name";
        String transformed = transformer.transformSql(sql);

        assertTrue("Transformed SQL should contain AND condition, got: " + transformed,
                   transformed.contains("active=true AND tenant_id=123 /* comment */ ORDER BY name"));
    }
}
