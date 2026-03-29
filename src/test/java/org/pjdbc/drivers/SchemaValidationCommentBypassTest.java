package org.pjdbc.drivers;

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
    public void testCommentBypass() throws SQLException {
        // Whitelist only 'allowed_table'
        String url = "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:test_schema_bypass";

        try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_schema_bypass")) {
            try (Statement stmt = setupConn.createStatement()) {
                stmt.execute("CREATE TABLE secret_table (id INT)");
                stmt.execute("CREATE TABLE allowed_table (id INT)");
            }

            try (Connection conn = DriverManager.getConnection(url)) {
                try (Statement stmt = conn.createStatement()) {
                    // This should be blocked
                    try {
                        stmt.executeQuery("SELECT * FROM/**/secret_table");
                        fail("Bypassed SchemaValidationDriver with comment between FROM and table name!");
                    } catch (SQLException e) {
                        if (!e.getMessage().contains("SchemaValidationDriver")) {
                            fail("Caught SQLException but not from SchemaValidationDriver: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
