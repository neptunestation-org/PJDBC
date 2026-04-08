package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.sql.SQLException;

public class TransformerCommentTest {

    @Test
    public void testSchemaTransformerWithComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("my_schema");

        // Basic case
        assertEquals("SELECT * FROM my_schema.users",
                     transformer.transformSql("SELECT * FROM users"));

        // Comment instead of space
        assertEquals("SELECT * FROM/*comment*/my_schema.users",
                     transformer.transformSql("SELECT * FROM/*comment*/users"));

        // Multiple comments and whitespace
        assertEquals("SELECT * FROM  /*c1*/  /*c2*/  my_schema.users",
                     transformer.transformSql("SELECT * FROM  /*c1*/  /*c2*/  users"));

        // Line comments
        assertEquals("SELECT * FROM -- comment\nmy_schema.users",
                     transformer.transformSql("SELECT * FROM -- comment\nusers"));

        // Mixed DML
        assertEquals("UPDATE my_schema.users SET name = 'foo'",
                     transformer.transformSql("UPDATE users SET name = 'foo'"));
        assertEquals("INSERT INTO my_schema.orders VALUES (1)",
                     transformer.transformSql("INSERT INTO orders VALUES (1)"));
    }
}
