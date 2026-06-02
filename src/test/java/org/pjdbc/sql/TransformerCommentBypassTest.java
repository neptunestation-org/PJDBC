package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {
    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Basic case
        assertEquals("SELECT * FROM tenant1.users", transformer.transformSql("SELECT * FROM users"));

        // Comment bypass attempt
        String sql = "SELECT * FROM/*bypass*/users";
        String transformed = transformer.transformSql(sql);
        assertEquals("SELECT * FROM/*bypass*/tenant1.users", transformed);

        // Multi-line comment
        String sql2 = "SELECT * FROM\n/* multi\nline */\nusers";
        String transformed2 = transformer.transformSql(sql2);
        assertTrue(transformed2.contains("tenant1.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // MODIFIABLE_STATEMENT bypass with comment
        String sql = "/* prefix */SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue(transformed.contains("WHERE tenant_id=1"));

        // WHERE_INSERTION_POINT bypass
        String sql2 = "SELECT * FROM users/*bypass*/ORDER BY id";
        String transformed2 = transformer.transformSql(sql2);
        assertTrue(transformed2.contains("WHERE tenant_id=1"));

        // WHERE_CLAUSE_END bypass
        String sql3 = "SELECT * FROM users WHERE active=true/*bypass*/ORDER BY id";
        String transformed3 = transformer.transformSql(sql3);
        assertTrue(transformed3.contains("active=true AND tenant_id=1"));
    }
}
