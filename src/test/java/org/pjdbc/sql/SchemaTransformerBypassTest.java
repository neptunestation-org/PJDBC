package org.pjdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaTransformer Bypass Tests")
class SchemaTransformerBypassTest {

    @Test
    @DisplayName("SchemaTransformer handles comments between keyword and table name")
    void handlesComments() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");

        // Block comment
        assertEquals("SELECT * FROM/**/tenant1.users",
            transformer.transformSql("SELECT * FROM/**/users"));

        // Line comment
        assertEquals("SELECT * FROM --\ntenant1.users",
            transformer.transformSql("SELECT * FROM --\nusers"));

        // Multiple separators
        assertEquals("SELECT * FROM  /* comment */ --\ntenant1.users",
            transformer.transformSql("SELECT * FROM  /* comment */ --\nusers"));
    }

    @Test
    @DisplayName("SchemaTransformer preserves original separator exactly")
    void preservesSeparator() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        String sql = "SELECT * FROM  /* mysterious comment */  users";
        String expected = "SELECT * FROM  /* mysterious comment */  tenant1.users";
        assertEquals(expected, transformer.transformSql(sql));
    }
}
