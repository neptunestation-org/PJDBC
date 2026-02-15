package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyDriverBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                fail("Expected DML blocked message, but got: " + e.getMessage());
            }
        }
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_line_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("-- line comment\n INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading line comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                fail("Expected DML blocked message, but got: " + e.getMessage());
            }
        }
    }
}
