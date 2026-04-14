package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        String sql = "SELECT * FROM/**/users";
        String transformed = transformer.transformSql(sql);
        // If it didn't transform, it's a bypass (it should be tenant1.users)
        assertEquals("SELECT * FROM tenant1.users", transformed.replaceAll("/\\*.*?\\*/", " ").replaceAll("\\s+", " ").trim());
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "/* comment */SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        // If it didn't transform, it's a bypass
        assertEquals("/* comment */SELECT * FROM users WHERE tenant_id=1", transformed);
    }
}
