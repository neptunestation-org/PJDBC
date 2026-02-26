package org.pjdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Transformers with Comments")
class TransformerCommentTest {

    @Test
    @DisplayName("SchemaTransformer handles comments as separators")
    void schemaTransformerHandlesComments() throws Exception {
        SchemaTransformer transformer = new SchemaTransformer("tenant123");

        // Block comments
        assertEquals("SELECT * FROM/**/tenant123.users",
            transformer.transformSql("SELECT * FROM/**/users"));

        // Line comments
        String sqlWithLineComment = "SELECT * FROM -- comment\nusers";
        String expected = "SELECT * FROM -- comment\ntenant123.users";
        assertEquals(expected, transformer.transformSql(sqlWithLineComment));

        // Multiple separators
        assertEquals("SELECT * FROM  /**/  tenant123.users",
            transformer.transformSql("SELECT * FROM  /**/  users"));
    }

    @Test
    @DisplayName("WhereTransformer handles leading comments")
    void whereTransformerHandlesLeadingComments() throws Exception {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        // Leading block comment
        assertEquals("/**/SELECT * FROM users WHERE tenant_id=1",
            transformer.transformSql("/**/SELECT * FROM users"));

        // Leading line comment
        assertEquals("-- comment\nSELECT * FROM users WHERE tenant_id=1",
            transformer.transformSql("-- comment\nSELECT * FROM users"));

        // Leading whitespace and comment
        assertEquals("  /* x */ SELECT * FROM users WHERE tenant_id=1",
            transformer.transformSql("  /* x */ SELECT * FROM users"));
    }

    @Test
    @DisplayName("WhereTransformer handles comments in insertion points")
    void whereTransformerHandlesCommentsInInsertionPoints() throws Exception {
        WhereTransformer transformer = new WhereTransformer("active=true");

        // Comment before ORDER BY
        assertEquals("SELECT * FROM users WHERE active=true/**/ORDER BY name",
            transformer.transformSql("SELECT * FROM users/**/ORDER BY name"));

        // Comment inside GROUP BY (if it matches)
        // Note: our regex matches the whole separator before GROUP BY
        assertEquals("SELECT * FROM users WHERE active=true  -- comment\nGROUP BY id",
            transformer.transformSql("SELECT * FROM users  -- comment\nGROUP BY id"));
    }
}
