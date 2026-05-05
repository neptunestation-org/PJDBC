package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerWithComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Simple case
        assertEquals("SELECT * FROM tenant1.users", transformer.transformSql("SELECT * FROM users"));

        // With comments
        String sql = "SELECT * FROM /* comment */ users";
        String transformed = transformer.transformSql(sql);
        assertEquals("SELECT * FROM /* comment */ tenant1.users", transformed);

        // Multiple tables with comments
        sql = "SELECT * FROM /* c1 */ t1 JOIN /* c2 */ t2 ON t1.id = t2.id";
        transformed = transformer.transformSql(sql);
        assertEquals("SELECT * FROM /* c1 */ tenant1.t1 JOIN /* c2 */ tenant1.t2 ON t1.id = t2.id", transformed);
    }

    @Test
    public void testWhereTransformerWithComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // Leading comments
        String sql = "/* leading */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertEquals("/* leading */ SELECT * FROM users WHERE tenant_id=123", transformed);

        // Comments before GROUP BY
        sql = "SELECT count(*) FROM users /* before group */ GROUP BY type";
        transformed = transformer.transformSql(sql);
        assertEquals("SELECT count(*) FROM users WHERE tenant_id=123 /* before group */ GROUP BY type", transformed);

        // Existing WHERE with comments
        sql = "SELECT * FROM users WHERE /* c */ active=true /* c2 */ ORDER BY name";
        transformed = transformer.transformSql(sql);
        assertTrue(transformed.contains("active=true"));
        assertTrue(transformed.contains("AND tenant_id=123"));
        assertTrue(transformed.contains("ORDER BY name"));
    }
}
