package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");
        String sql = "SELECT * FROM/**/users";
        String transformed = transformer.transformSql(sql);
        // If bypassed, it will be "SELECT * FROM/**/users"
        // If fixed, it should be "SELECT * FROM/**/tenant123.users"
        assertTrue("Expected schema prefix in: " + transformed, transformed.contains("tenant123.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "/* comment */SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        // If bypassed, it will be just the original SQL because MODIFIABLE_STATEMENT pattern didn't match
        assertTrue("Expected WHERE clause in: " + transformed, transformed.contains("WHERE tenant_id=1"));
    }

    @Test
    public void testWhereTransformerInsertionPointBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "SELECT * FROM users/**/ORDER BY name";
        String transformed = transformer.transformSql(sql);
        // If bypassed, WHERE might be appended at the end or not at all correctly
        assertTrue("Expected WHERE before ORDER BY in: " + transformed,
            transformed.contains("WHERE tenant_id=1 ORDER BY name") ||
            transformed.contains("WHERE tenant_id=1/**/ORDER BY name"));
    }

    @Test
    public void testSchemaTransformerWithSpecialCharsInComment() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");
        // Comment containing $ and \ which are special in appendReplacement
        String sql = "SELECT * FROM/* cost $100 \temp */users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected schema prefix in: " + transformed, transformed.contains("tenant123.users"));
        assertTrue("Expected original comment preserved in: " + transformed, transformed.contains("/* cost $100 \temp */"));
    }
}
