package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentTest {
    @Test
    public void testSchemaTransformerPreservesComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Block comment as separator
        String sql1 = "SELECT * FROM /* comment */ users";
        String transformed1 = transformer.transformSql(sql1);
        assertEquals("SELECT * FROM /* comment */ tenant1.users", transformed1);

        // Line comment as separator
        String sql2 = "SELECT * FROM -- comment\nusers";
        String transformed2 = transformer.transformSql(sql2);
        assertEquals("SELECT * FROM -- comment\ntenant1.users", transformed2);
    }

    @Test
    public void testWhereTransformerPreservesComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // Block comment before insertion point
        String sql1 = "SELECT * FROM users /* comment */ ORDER BY name";
        String transformed1 = transformer.transformSql(sql1);
        assertEquals("SELECT * FROM users WHERE tenant_id=123 /* comment */ ORDER BY name", transformed1);

        // Multiple separators and comments
        String sql2 = "SELECT * FROM users \n /* comment */ \n ORDER BY name";
        String transformed2 = transformer.transformSql(sql2);
        assertEquals("SELECT * FROM users WHERE tenant_id=123 \n /* comment */ \n ORDER BY name", transformed2);
    }

    @Test
    public void testWhereTransformerLeadingComment() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        String sql = "/* leading */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue(transformed.startsWith("/* leading */"));
        assertTrue(transformed.contains("WHERE tenant_id=123"));
    }
}
