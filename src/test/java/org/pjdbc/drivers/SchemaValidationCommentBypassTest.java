package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationCommentBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Bypass using comment instead of space
                // H2 might require at least one space or it might accept the comment as a separator
                // Standard SQL often allows comments as separators.
                try {
                    stmt.execute("SELECT * FROM/*comment*/secret_table");
                    fail("Expected SQLException for blocked table 'secret_table'");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        // If it failed because of syntax error in H2, that's one thing,
                        // but if it didn't fail or failed with H2 error, then SchemaValidationDriver bypassed.
                        fail("Bypassed SchemaValidationDriver! Error was: " + e.getMessage());
                    }
                }
            }
        }
    }
}
