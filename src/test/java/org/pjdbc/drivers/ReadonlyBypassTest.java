package org.pjdbc.drivers;

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
                // This SHOULD be blocked but might bypass due to leading comment
                stmt.execute("/* comment */ INSERT INTO test_table VALUES (1)");
                // If it doesn't throw, we've bypassed it (or the table doesn't exist,
                // but ReadonlyDriver should check the SQL BEFORE sending to DB)
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("DML blocked")) {
                return; // Success, it was blocked
            }
            // If it's a different SQLException (like table not found),
            // it means it passed the ReadonlyDriver check.
            fail("Passed ReadonlyDriver check but failed in DB: " + e.getMessage());
        }
        fail("Completely bypassed ReadonlyDriver check");
    }

    @Test
    public void testCTEBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // CTE with DML
                stmt.execute("WITH deleted AS (DELETE FROM some_table RETURNING *) SELECT * FROM deleted");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("DML blocked")) {
                return;
            }
            fail("Passed ReadonlyDriver check but failed in DB: " + e.getMessage());
        }
        fail("Completely bypassed ReadonlyDriver check (CTE)");
    }
}
