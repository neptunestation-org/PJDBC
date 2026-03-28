package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaValidationBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
    }

    @Test
    public void testCommentSeparatorBypass() throws SQLException {
        String dbName = "test_schema_bypass_" + System.currentTimeMillis();
        String url = "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        String directUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        // Setup table
        try (Connection setupConn = DriverManager.getConnection(directUrl)) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE secrets (id INT, val VARCHAR(255))");
                stmt.execute("INSERT INTO secrets VALUES (1, 'sensitive data')");
            }
        }

        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // Try normal blocked access first
                try {
                    stmt.executeQuery("SELECT * FROM secrets");
                    fail("Should have blocked access to 'secrets' table");
                } catch (SQLException e) {
                    assertTrue(e.getMessage().contains("is blocked"));
                }

                // Now try bypass with comment instead of space
                // SchemaValidationDriver uses \\s+ which won't match if there's only a comment
                String sql = "SELECT * FROM/**/secrets";
                try (var rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        fail("Bypass successful: Accessed blocked table 'secrets' via comment separator");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }
}
