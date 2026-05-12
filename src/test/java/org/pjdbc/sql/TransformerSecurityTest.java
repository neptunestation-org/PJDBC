package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerSecurityTest {

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "/* comment */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);

        // If it bypassed, it will be equal to the original
        assertFalse("WhereTransformer bypassed by leading comment", sql.equals(transformed));
        assertTrue("WhereTransformer should have appended WHERE clause", transformed.contains("WHERE tenant_id=1"));
    }

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("myschema");
        String sql = "SELECT * FROM/**/users";
        String transformed = transformer.transformSql(sql);

        assertFalse("SchemaTransformer bypassed by comment separator", sql.equals(transformed));
        assertTrue("SchemaTransformer should have prefixed table", transformed.contains("myschema.users"));
    }
}
