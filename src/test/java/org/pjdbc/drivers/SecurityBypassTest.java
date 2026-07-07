package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SecurityBypassTest {

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:schema_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked by SchemaValidationDriver
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT /* bypass */ * FROM /* bypass */ blocked_table")
                );
                assertTrue(ex.getMessage().contains("blocked_table"), "Error message should mention the blocked table");
                assertTrue(ex.getMessage().contains("SchemaValidationDriver"), "Error should come from SchemaValidationDriver");
            }
        }
    }

    @Test
    public void testSchemaValidationColumnCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedColumns=secret_col]:jdbc:h2:mem:schema_col_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT secret_col /* comment */ FROM some_table")
                );
                assertTrue(ex.getMessage().contains("secret_col"));
            }
        }
    }

    @Test
    public void testSchemaValidationMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:schema_multiline_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT\n/*\ncomment\n*/\n*\nFROM\n/*\ncomment\n*/\nblocked_table")
                );
                assertTrue(ex.getMessage().contains("blocked_table"));
            }
        }
    }
}
