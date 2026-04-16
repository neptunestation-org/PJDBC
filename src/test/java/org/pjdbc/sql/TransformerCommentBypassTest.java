package org.pjdbc.sql;

import java.sql.SQLException;
import org.junit.Assert;
import org.junit.Test;

public class TransformerCommentBypassTest {
    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant_123");

        // Standard case
        String sql = "SELECT * FROM users";
        String expected = "SELECT * FROM tenant_123.users";
        Assert.assertEquals(expected, transformer.transformSql(sql));

        // Bypass with comment
        String bypassSql = "SELECT * FROM /* comment */ users";
        String result = transformer.transformSql(bypassSql);

        // If it didn't transform, it's a bypass
        if (!result.contains("tenant_123.users")) {
            Assert.fail("SchemaTransformer failed to transform SQL with comment: " + result);
        }
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=123");

        // Standard case
        String sql = "SELECT * FROM users";
        String result = transformer.transformSql(sql);
        Assert.assertTrue(result.contains("WHERE tenant_id=123"));

        // Bypass with leading comment
        String bypassSql = "/* leading comment */ SELECT * FROM users";
        String bypassResult = transformer.transformSql(bypassSql);

        if (!bypassResult.contains("WHERE tenant_id=123")) {
            Assert.fail("WhereTransformer failed to transform SQL with leading comment: " + bypassResult);
        }
    }
}
