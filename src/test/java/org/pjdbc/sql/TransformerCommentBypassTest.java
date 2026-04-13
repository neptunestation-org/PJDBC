package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerComment() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        String sql = "SELECT * FROM/*comment*/users";
        String transformed = transformer.transformSql(sql);
        assertEquals("SELECT * FROM/*comment*/tenant1.users", transformed);
    }

    @Test
    public void testWhereTransformerComment() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Leading comment
        String sql1 = "/*comment*/SELECT * FROM users";
        String transformed1 = transformer.transformSql(sql1);
        assertTrue("Expected WHERE clause to be added: " + transformed1,
            transformed1.contains("WHERE tenant_id=1"));

        // Comment as separator before ORDER BY
        String sql2 = "SELECT * FROM users/*comment*/ORDER BY name";
        String transformed2 = transformer.transformSql(sql2);
        assertTrue("Expected WHERE clause before ORDER BY: " + transformed2,
            transformed2.contains("WHERE tenant_id=1/*comment*/ORDER BY name"));
    }
}
