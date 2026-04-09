package org.pjdbc.drivers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationCommentBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testTableCommentBypass() throws SQLException {
        // Block "secret_table"
        String baseH2Url = "jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        String url = "jdbc:schema[blockedTables=secret_table,mode=blacklist]:" + baseH2Url;

        try (Connection setupConn = DriverManager.getConnection(baseH2Url)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE secret_table (id INT)");
                stmt.execute("INSERT INTO secret_table VALUES (1)");
            }

            try (Connection conn = DriverManager.getConnection(url)) {
                try (Statement stmt = conn.createStatement()) {
                    // Comment between FROM and table name might bypass
                    try (java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM /* comment */ secret_table")) {
                        if (rs.next()) {
                            fail("SchemaValidationDriver was bypassed using comment between FROM and table name!");
                        }
                    }
                }
            } catch (SQLException e) {
                // Expected if not bypassed
                System.out.println("Caught expected exception: " + e.getMessage());
                assertTrue("Should be blocked by SchemaValidationDriver", e.getMessage().contains("SchemaValidationDriver"));
            }
        }
    }
}
