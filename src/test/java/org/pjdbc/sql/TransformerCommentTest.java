package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import org.junit.Test;

public class TransformerCommentTest {

    @Test
    public void testSchemaTransformerWithComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Block comment between FROM and table
        String sql1 = "SELECT * FROM/**/users";
        assertEquals("SELECT * FROM/**/tenant1.users", transformer.transformSql(sql1));

        // Multiple separators
        String sql2 = "SELECT * FROM \n -- comment\n /* block */ users";
        String transformed2 = transformer.transformSql(sql2);
        assertTrue(transformed2.contains("tenant1.users"));
        assertTrue(transformed2.contains("-- comment"));
        assertTrue(transformed2.contains("/* block */"));
    }

    @Test
    public void testWhereTransformerWithLeadingComments() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Leading comment
        String sql1 = "/* some comment */ SELECT * FROM users";
        String transformed1 = transformer.transformSql(sql1);
        assertTrue(transformed1.startsWith("/* some comment */ SELECT"));
        assertTrue(transformed1.contains("WHERE tenant_id=1"));

        // Comment before GROUP BY
        String sql2 = "SELECT * FROM users /* comment */ GROUP BY id";
        String transformed2 = transformer.transformSql(sql2);
        assertTrue(transformed2.contains("WHERE tenant_id=1 /* comment */ GROUP BY id"));
    }
}
