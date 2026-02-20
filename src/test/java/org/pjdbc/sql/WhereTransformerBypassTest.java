package org.pjdbc.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WhereTransformer Bypass Tests")
class WhereTransformerBypassTest {

    @Test
    @DisplayName("WhereTransformer can be bypassed with leading comment")
    void bypassWithLeadingComment() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "/**/DELETE FROM users";
        String transformed = transformer.transformSql(sql);

        // If bypassed, it will be equal to original
        assertNotEquals(sql, transformed, "Should have transformed SQL even with leading comment");
        assertTrue(transformed.contains("WHERE tenant_id=1"), "Transformed SQL should contain WHERE clause");
    }

    @Test
    @DisplayName("WhereTransformer can be bypassed with comment instead of space before insertion point")
    void bypassWithCommentInsteadOfSpace() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");

        String sql = "SELECT * FROM users/**/ORDER BY id";
        String transformed = transformer.transformSql(sql);

        // If the regex requires \s+, and we only have /**/ then it might fail
        // Wait, "users/**/ORDER" -> no space between users and /**/.

        assertTrue(transformed.contains("WHERE tenant_id=1"), "Should have inserted WHERE before ORDER BY even with comments");
    }
}
