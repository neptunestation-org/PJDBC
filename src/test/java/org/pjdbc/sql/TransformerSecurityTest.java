package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;

import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant_1");

        String sql = "SELECT * FROM /* comment */ users";
        String result = transformer.transformSql(sql);

        // If matched, it should contain tenant_1.
        assertEquals("SELECT * FROM /* comment */ tenant_1.users", result);
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        String sql = "/* comment */ SELECT * FROM users";
        String result = transformer.transformSql(sql);

        assertEquals("/* comment */ SELECT * FROM users WHERE tenant_id=1", result);
    }
}
