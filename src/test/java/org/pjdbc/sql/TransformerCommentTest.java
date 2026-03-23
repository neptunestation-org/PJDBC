package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentTest {

    @Test
    public void testWhereTransformerWithComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        String sql = "/* leading comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue("Should contain WHERE tenant_id=1", transformed.contains("WHERE tenant_id=1"));

        sql = "SELECT * FROM/**/users";
        // WhereTransformer only checks if statement is modifiable and if it has WHERE.
        // It doesn't strictly need to parse the FROM part if it's just appending at the end.
        transformed = transformer.transformSql(sql);
        assertTrue("Should contain WHERE tenant_id=1", transformed.contains("WHERE tenant_id=1"));
    }

    @Test
    public void testSchemaTransformerWithComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("myschema");

        String sql = "SELECT * FROM/**/users";
        String transformed = transformer.transformSql(sql);
        // Original TABLE_PATTERN used \\s+, so it would miss FROM/**/users
        assertTrue("Should prefix with myschema.", transformed.contains("myschema.users"));

        sql = "UPDATE/* comment */users SET x=1";
        transformed = transformer.transformSql(sql);
        assertTrue("Should prefix with myschema.", transformed.contains("myschema.users"));
    }
}
