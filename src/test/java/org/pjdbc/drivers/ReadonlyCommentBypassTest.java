package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyCommentBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:readonly:" + h2Url;

        // Create table first
        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked but might bypass if it starts with a comment
                stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                // If it doesn't throw SQLException, then it's a bypass
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                return; // Correctly blocked
            }
            throw e;
        }
        fail("Bypass successful: INSERT with leading comment was not blocked");
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_bypass_line;DB_CLOSE_DELAY=-1";
        String url = "jdbc:readonly:" + h2Url;

        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- line comment\nINSERT INTO test VALUES (1)");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver")) {
                return;
            }
            throw e;
        }
        fail("Bypass successful: INSERT with leading line comment was not blocked");
    }
}
