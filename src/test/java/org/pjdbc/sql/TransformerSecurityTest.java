package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {
    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        // Mixed comments and whitespace
        String sql = "SELECT * FROM/*comment*/users";
        String expected = "SELECT * FROM/*comment*/tenant1.users";
        assertEquals(expected, transformer.transformSql(sql));
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        // Leading comment
        String sql = "/* leading */SELECT * FROM users";
        String expected = "/* leading */SELECT * FROM users WHERE tenant_id=1";
        assertEquals(expected, transformer.transformSql(sql));
    }

    @Test
    public void testWhereTransformerInsertionPointCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        // Comment before ORDER BY
        String sql = "SELECT * FROM users/*comment*/ORDER BY id";
        String expected = "SELECT * FROM users WHERE tenant_id=1/*comment*/ORDER BY id";
        assertEquals(expected, transformer.transformSql(sql));
    }
}
