package org.pjdbc.drivers;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class ReadonlyBypassTest {
    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        // This should be blocked, but if it bypasses, it will try to execute on the mock driver
        String bypassSql = "/* comment */ DELETE FROM users";

        try {
            stmt.execute(bypassSql);
            fail("Should have thrown SQLException for blocked DML with leading comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("ReadonlyDriver"), "Expected ReadonlyDriver error message, got: " + e.getMessage());
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        String bypassSql = "-- line comment\nDELETE FROM users";

        try {
            stmt.execute(bypassSql);
            fail("Should have thrown SQLException for blocked DML with leading line comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("ReadonlyDriver"));
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        String bypassSql = "/* \n multi-line \n comment \n */ DELETE FROM users";

        try {
            stmt.execute(bypassSql);
            fail("Should have thrown SQLException for blocked DML with multi-line comment");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("ReadonlyDriver"));
        }
    }

    @Test
    public void testNestedCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:mock:test";
        Properties info = new Properties();
        Connection conn = DriverManager.getConnection(url, info);
        Statement stmt = conn.createStatement();

        String bypassSql = "/* outer /* inner */ outer */ DELETE FROM users";

        try {
            stmt.execute(bypassSql);
            fail("Should have thrown SQLException for blocked DML with nested comments");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("ReadonlyDriver"));
        }
    }
}
