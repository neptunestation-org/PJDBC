package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

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
                // This should be blocked, but might bypass if regex only looks at start of string
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            } catch (SQLException e) {
                assertTrue("Expected error message to contain 'DML blocked', but was: " + e.getMessage(),
                           e.getMessage().contains("DML blocked"));
            }
        }
    }

    @Test
    public void testCTEWithDML() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_security_cte";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // This should be blocked, but might bypass if regex doesn't handle WITH clauses
                stmt.execute("WITH deleted AS (DELETE FROM some_table RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for DELETE inside CTE");
            } catch (SQLException e) {
                assertTrue("Expected error message to contain 'DML blocked', but was: " + e.getMessage(),
                           e.getMessage().contains("DML blocked"));
            }
        }
    }
}
