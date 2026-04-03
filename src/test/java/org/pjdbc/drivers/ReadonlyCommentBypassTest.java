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

    private void assertBlocked(String sql) throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                fail("Expected SQLException for: " + sql);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("ReadonlyDriver") && e.getMessage().contains("blocked")) {
                // Correctly blocked
                return;
            }
            fail("Expected ReadonlyDriver to block the statement, but got: " + e.getMessage());
        }
    }

    @Test
    public void testBlockCommentBypass() throws SQLException {
        assertBlocked("/* bypass */ INSERT INTO test_table VALUES (1)");
    }

    @Test
    public void testLineCommentBypass() throws SQLException {
        assertBlocked("-- bypass\nINSERT INTO test_table VALUES (1)");
    }

    @Test
    public void testMultiLineCommentBypass() throws SQLException {
        assertBlocked("/* \n multi \n line \n */ UPDATE test_table SET x=1");
    }

    @Test
    public void testMixedCommentBypass() throws SQLException {
        assertBlocked("/* comm */ -- line \n /* comm */ DELETE FROM test_table");
    }

    @Test
    public void testDDLCommentBypass() throws SQLException {
        assertBlocked("/* bypass */ CREATE TABLE test (id INT)");
    }

    @Test
    public void testDCLCommentBypass() throws SQLException {
        assertBlocked("/* bypass */ GRANT SELECT ON test TO user");
    }
}
