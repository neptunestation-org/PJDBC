package org.pjdbc.drivers;

import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationDriverSecurityTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        // Block "secret_table"
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                try {
                    stmt.executeUpdate("/* comment */ INSERT INTO secret_table VALUES (1)");
                    fail("Expected SQLException for blocked table with leading comment");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        fail("Expected SchemaValidationDriver to block the statement: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void testSeparatorCommentBypass() throws SQLException {
        // Block "secret_table"
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:jdbc:h2:mem:test_schema_sep_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked. Note the comment between INTO and table name.
                try {
                    stmt.executeUpdate("INSERT INTO/**/secret_table VALUES (1)");
                    fail("Expected SQLException for blocked table with comment separator");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        fail("Expected SchemaValidationDriver to block the statement: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        // Block "secret_column"
        String url = "jdbc:schema[blockedColumns=secret_column,mode=blacklist]:jdbc:h2:mem:test_schema_col_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This SHOULD be blocked
                try {
                    stmt.executeQuery("SELECT/**/secret_column/**/FROM/**/some_table");
                    fail("Expected SQLException for blocked column with comment separator");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("SchemaValidationDriver")) {
                        fail("Expected SchemaValidationDriver to block the statement: " + e.getMessage());
                    }
                }
            }
        }
    }
}
