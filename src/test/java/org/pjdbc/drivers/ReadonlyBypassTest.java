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
                // Ensure the driver blocks it BEFORE it reaches H2.
                // We don't even need the table to exist if the driver is doing its job.
                stmt.execute("/* bypass */ INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT with leading comment");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                // If it reached H2, it will likely be a "Table not found" or "Syntax error"
                // rather than a "DML blocked" error from ReadonlyDriver.
                fail("Statement bypassed ReadonlyDriver and reached the database: " + e.getMessage());
            }
        }
    }

    @Test
    public void testCteBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_cte_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Even if H2 doesn't support DELETE in CTE, ReadonlyDriver should ideally block it
                // if it detects the DELETE keyword in a DML context.
                stmt.execute("WITH deleted AS (DELETE FROM test_table RETURNING *) SELECT * FROM deleted");
                fail("Expected SQLException for CTE with DML");
            }
        } catch (SQLException e) {
            if (!e.getMessage().contains("DML blocked")) {
                fail("Statement bypassed ReadonlyDriver and reached the database: " + e.getMessage());
            }
        }
    }
}
