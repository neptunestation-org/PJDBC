package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlySecurityTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ INSERT INTO test VALUES (1)");
                fail("Should have blocked INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver error, but got: " + e.getMessage(),
                           e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_line";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- comment\nINSERT INTO test VALUES (1)");
                fail("Should have blocked INSERT with leading line comment");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver error, but got: " + e.getMessage(),
                           e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
