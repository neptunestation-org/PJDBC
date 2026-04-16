package org.pjdbc.drivers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class ReadonlyCommentBypassTest {
    @Test
    public void testCommentBypass() throws SQLException {
        // Use H2 in-memory database
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_bypass;DB_CLOSE_DELAY=-1";
        Properties info = new Properties();
        info.setProperty("user", "sa");
        info.setProperty("password", "");

        try (Connection conn = DriverManager.getConnection(url, info)) {
            try (Statement stmt = conn.createStatement()) {
                // Setup: create a table (direct connection would be better but let's see if we can do it here if allowDDL=true is default, but it's false)
            }
        }

        // Let's use a direct connection for setup
        String directUrl = "jdbc:h2:mem:test_readonly_bypass;DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(directUrl, info)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url, info)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                try {
                    stmt.execute("INSERT INTO test VALUES (1)");
                    Assert.fail("INSERT should have been blocked");
                } catch (SQLException e) {
                    // Expected
                }

                // This might bypass if comments are not handled
                try {
                    stmt.execute("/* comment */ INSERT INTO test VALUES (2)");
                    // If we reach here, it bypassed!
                } catch (SQLException e) {
                    // It was correctly blocked
                    if (e.getMessage().contains("ReadonlyDriver")) {
                        return;
                    }
                    throw e;
                }
                Assert.fail("INSERT with comment bypassed ReadonlyDriver!");
            }
        }
    }
}
