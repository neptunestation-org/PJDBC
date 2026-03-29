package org.pjdbc.drivers;

import static org.junit.Assert.fail;
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
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        // Create table in direct connection
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked
                try {
                    stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                    fail("Bypassed ReadonlyDriver with leading comment!");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        fail("Caught SQLException but not from ReadonlyDriver: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Test
    public void testCommentBetweenKeywordsBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass_between";
        // Create table in direct connection
        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_bypass_between")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // The current regex uses \b which might handle this, but let's check
                // "TRUNCATE/**/TABLE test" - the TRUNCATE regex is ^\s*(INSERT|...|TRUNCATE)\b
                try {
                    stmt.execute("TRUNCATE/**/TABLE test");
                    fail("Bypassed ReadonlyDriver with comment between keywords!");
                } catch (SQLException e) {
                    if (!e.getMessage().contains("ReadonlyDriver")) {
                        fail("Caught SQLException but not from ReadonlyDriver: " + e.getMessage());
                    }
                }
            }
        }
    }
}
