package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("deleted=false");

        // Test MODIFIABLE_STATEMENT with leading comment
        String sql1 = "/* comment */ SELECT * FROM users";
        String expected1 = "/* comment */ SELECT * FROM users WHERE deleted=false";
        assertEquals(expected1, transformer.transformSql(sql1));

        // Test WHERE_INSERTION_POINT with comment
        String sql2 = "SELECT * FROM users /* comment */ ORDER BY name";
        String expected2 = "SELECT * FROM users WHERE deleted=false /* comment */ ORDER BY name";
        assertEquals(expected2, transformer.transformSql(sql2));

        // Test existing WHERE with comment
        String sql3 = "SELECT * FROM users WHERE /* comment */ id=1";
        String transformed3 = transformer.transformSql(sql3);
        assertTrue("Expected AND deleted=false in: " + transformed3, transformed3.contains("AND deleted=false"));
    }

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant_123");

        // Test TABLE_PATTERN with comment
        String sql = "SELECT * FROM /* comment */ users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Expected tenant_123.users, but got: " + transformed, transformed.contains("tenant_123.users"));
        assertTrue("Expected comment to be preserved, but got: " + transformed, transformed.contains("/* comment */"));
    }
}
