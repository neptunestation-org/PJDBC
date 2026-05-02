package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;

import org.junit.Test;

public class TransformerCommentBypassTest {

    @Test
    public void testSchemaTransformerCommentBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("myschema");
        String sql = "SELECT * FROM/*comment*/users";
        String transformed = transformer.transformSql(sql);
        // If it fails to see the comment as a separator, it won't transform "users"
        assertEquals("SELECT * FROM/*comment*/myschema.users", transformed);
    }

    @Test
    public void testWhereTransformerCommentBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("deleted=false");

        // Leading comment
        assertEquals("/* comment */ SELECT * FROM users WHERE deleted=false",
                     transformer.transformSql("/* comment */ SELECT * FROM users"));

        // Comment before ORDER BY
        assertEquals("SELECT * FROM users WHERE deleted=false /* comment */ ORDER BY name",
                     transformer.transformSql("SELECT * FROM users /* comment */ ORDER BY name"));

        // Comment after WHERE
        assertEquals("SELECT * FROM users WHERE active=true AND deleted=false /* comment */ ORDER BY name",
                     transformer.transformSql("SELECT * FROM users WHERE active=true /* comment */ ORDER BY name"));

        // No WHERE, comment before ORDER BY
        assertEquals("SELECT * FROM users WHERE deleted=false /* comment */ ORDER BY name",
                     transformer.transformSql("SELECT * FROM users /* comment */ ORDER BY name"));
    }
}
