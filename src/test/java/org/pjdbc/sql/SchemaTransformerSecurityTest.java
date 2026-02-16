package org.pjdbc.sql;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;

import org.junit.Test;

public class SchemaTransformerSecurityTest {

    @Test
    public void testCommentAwareTransformation() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");

        // Basic transformation
        assertEquals("SELECT * FROM tenant123.users", transformer.transformSql("SELECT * FROM users"));

        // Transformation with comments
        assertEquals("SELECT * FROM/**/tenant123.users", transformer.transformSql("SELECT * FROM/**/users"));
        assertEquals("SELECT * FROM  /* comment */  tenant123.users", transformer.transformSql("SELECT * FROM  /* comment */  users"));
        assertEquals("SELECT * FROM -- comment\ntenant123.users", transformer.transformSql("SELECT * FROM -- comment\nusers"));

        // Multiple joins with comments
        String sql = "SELECT * FROM users JOIN/**/orders ON users.id = orders.user_id";
        String expected = "SELECT * FROM tenant123.users JOIN/**/tenant123.orders ON users.id = orders.user_id";
        assertEquals(expected, transformer.transformSql(sql));
    }

    @Test
    public void testMultiLineComment() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");
        String sql = "SELECT * FROM /* multi\nline\ncomment */ users";
        String expected = "SELECT * FROM /* multi\nline\ncomment */ tenant123.users";
        assertEquals(expected, transformer.transformSql(sql));
    }
}
