package org.pjdbc.sql;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");
        String sql = "SELECT * FROM/*comment*/users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected transformed SQL to contain tenant123.users, but was: " + transformed,
                   transformed.contains("tenant123.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "SELECT * FROM users/*comment*/ORDER BY id";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected transformed SQL to contain WHERE tenant_id=1, but was: " + transformed,
                   transformed.contains("WHERE tenant_id=1"));
    }

    @Test
    public void testWhereTransformerModifiableBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        // Leading comment bypass for MODIFIABLE_STATEMENT pattern
        String sql = "/*comment*/SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected transformed SQL to be modified, but was: " + transformed,
                   transformed.contains("WHERE tenant_id=1"));
    }

    @Test
    public void testSchemaTransformerWithSpecialCharsInComment() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");
        // Comment containing $ and \ which are special in Matcher.appendReplacement
        String sql = "SELECT * FROM /* cost $100 \\ price */ users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected transformed SQL to contain tenant123.users, but was: " + transformed,
                   transformed.contains("tenant123.users"));
        assertTrue("Expected transformed SQL to preserve the special characters in comment, but was: " + transformed,
                   transformed.contains("cost $100 \\ price"));
    }
}
