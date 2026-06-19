package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyBypassTest {
    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ DELETE FROM some_table");
                fail("ReadonlyDriver bypassed by leading comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_multiline_comment_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* \n multi-line \n comment \n */\n UPDATE some_table SET x=1");
                fail("ReadonlyDriver bypassed by multi-line comment");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This matches CTE_DML_PATTERN and should be blocked before reaching H2
                stmt.execute("WITH t AS (DELETE FROM some_table) SELECT * FROM t");
                fail("ReadonlyDriver bypassed by CTE");
            }
        } catch (SQLException e) {
            assertTrue("Expected 'blocked' in message: " + e.getMessage(),
                       e.getMessage().contains("blocked"));
        }
    }
}
