package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Standard case
        assertEquals("SELECT * FROM tenant1.users", transformer.transformSql("SELECT * FROM users"));

        // Comment bypass
        String sql = "SELECT * FROM /* comment */ users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Bypassed SchemaTransformer with interspersed comment! Result: " + transformed,
                   transformed.contains("tenant1.users"));

        sql = "/* comment */ SELECT * FROM users";
        transformed = transformer.transformSql(sql);
        assertTrue("Bypassed SchemaTransformer with leading comment! Result: " + transformed,
                   transformed.contains("tenant1.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Standard case
        assertEquals("SELECT * FROM users WHERE tenant_id=1", transformer.transformSql("SELECT * FROM users"));

        // Comment bypass
        String sql = "SELECT * FROM users /* comment */";
        String transformed = transformer.transformSql(sql);
        assertTrue("Bypassed WhereTransformer with trailing comment! Result: " + transformed,
                   transformed.contains("WHERE tenant_id=1"));

        sql = "/* comment */ SELECT * FROM users";
        transformed = transformer.transformSql(sql);
        assertTrue("Bypassed WhereTransformer with leading comment! Result: " + transformed,
                   transformed.contains("WHERE tenant_id=1"));

        sql = "SELECT * FROM users WHERE active=true /* comment */";
        transformed = transformer.transformSql(sql);
        assertTrue("Bypassed WhereTransformer with existing WHERE and comment! Result: " + transformed,
                   transformed.contains("AND tenant_id=1"));
    }
}
