package org.pjdbc.drivers;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaValidationBypassTest {
    @Test
    public void testTableCommentBypass() throws SQLException {
        // Whitelist mode, only allow 'users' table
        String url = "jdbc:schema[allowedTables=users]:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        // This should be blocked if it tries to access 'secrets'
        String bypassSql = "SELECT * FROM /* comment */ secrets";

        try {
            stmt.executeQuery(bypassSql);
            fail("Should have thrown SQLException for blocked table with comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("SchemaValidationDriver"), "Expected SchemaValidationDriver error message, got: " + e.getMessage());
        }
    }

    @Test
    public void testColumnCommentBypass() throws SQLException {
        // Block 'ssn' column
        String url = "jdbc:schema[blockedColumns=ssn]:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        // This should be blocked
        String bypassSql = "SELECT /* comment */ ssn FROM users";

        try {
            stmt.executeQuery(bypassSql);
            fail("Should have thrown SQLException for blocked column with comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("SchemaValidationDriver"), "Expected SchemaValidationDriver error message, got: " + e.getMessage());
        }
    }

    @Test
    public void testInsertColumnCommentBypass() throws SQLException {
        // Block 'password' column
        String url = "jdbc:schema[blockedColumns=password]:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        // This should be blocked
        String bypassSql = "INSERT INTO users /* comment */ (password) VALUES ('secret')";

        try {
            stmt.execute(bypassSql);
            fail("Should have thrown SQLException for blocked INSERT column with comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("SchemaValidationDriver"));
        }
    }
}
