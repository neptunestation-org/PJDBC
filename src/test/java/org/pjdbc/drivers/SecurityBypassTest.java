package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pjdbc.sql.SchemaTransformer;
import org.pjdbc.sql.WhereTransformer;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDrivers() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FederatingDriver");
    }

    @Test
    public void testReadonlyCommentBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_readonly_bypass;DB_CLOSE_DELAY=-1";
        String pjdbcUrl = "jdbc:readonly:" + h2Url;

        // Setup
        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            setupConn.createStatement().execute("CREATE TABLE IF NOT EXISTS test (id INT)");
        }

        try (Connection conn = DriverManager.getConnection(pjdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Try to bypass with a leading comment
                try {
                    stmt.execute("/* bypass */ INSERT INTO test VALUES (1)");
                    fail("ReadonlyDriver should have blocked INSERT with leading comment");
                } catch (SQLException e) {
                    assertTrue("Expected ReadonlyDriver error message, got: " + e.getMessage(), e.getMessage().contains("ReadonlyDriver"));
                }
            }
        }
    }

    @Test
    public void testSchemaValidationCommentBypass() throws SQLException {
        String h2Url = "jdbc:h2:mem:test_schema_bypass;DB_CLOSE_DELAY=-1";
        String pjdbcUrl = "jdbc:schema[allowedTables=allowed_table]:" + h2Url;

        // Setup
        try (Connection setupConn = DriverManager.getConnection(h2Url)) {
            setupConn.createStatement().execute("CREATE TABLE IF NOT EXISTS allowed_table (id INT)");
            setupConn.createStatement().execute("CREATE TABLE IF NOT EXISTS blocked_table (id INT)");
        }

        try (Connection conn = DriverManager.getConnection(pjdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Try to bypass with a comment between keywords
                try {
                    stmt.execute("SELECT * FROM /* bypass */ blocked_table");
                    fail("SchemaValidationDriver should have blocked access to blocked_table");
                } catch (SQLException e) {
                    assertTrue("Expected SchemaValidationDriver error message, got: " + e.getMessage(), e.getMessage().contains("SchemaValidationDriver"));
                }
            }
        }
    }

    @Test
    public void testFederatingDriverRoutingBypass() throws SQLException {
        String db0Url = "jdbc:h2:mem:db0f;DB_CLOSE_DELAY=-1";
        String db1Url = "jdbc:h2:mem:db1f;DB_CLOSE_DELAY=-1";
        String pjdbcUrl = "jdbc:federate[tableRouting=table1:0]:" + db0Url + ";" + db1Url;

        // Setup: Create table1 in both dbs with different data
        try (Connection conn0 = DriverManager.getConnection(db0Url)) {
            conn0.createStatement().execute("CREATE TABLE IF NOT EXISTS table1 (id INT)");
            conn0.createStatement().execute("TRUNCATE TABLE table1");
            conn0.createStatement().execute("INSERT INTO table1 VALUES (0)");
        }
        try (Connection conn1 = DriverManager.getConnection(db1Url)) {
            conn1.createStatement().execute("CREATE TABLE IF NOT EXISTS table1 (id INT)");
            conn1.createStatement().execute("TRUNCATE TABLE table1");
            conn1.createStatement().execute("INSERT INTO table1 VALUES (1)");
        }

        try (Connection conn = DriverManager.getConnection(pjdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Normal routing works: should only hit db0
                int count = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM table1")) {
                    while (rs.next()) {
                        assertEquals(0, rs.getInt(1));
                        count++;
                    }
                }
                assertEquals(1, count);

                // Bypass routing with comment: should broadcast to both and return 2 rows
                count = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM /* bypass */ table1")) {
                    while (rs.next()) {
                        count++;
                    }
                }
                // If extraction fails, it broadcasts.
                if (count > 1) {
                    fail("FederatingDriver broadcasted instead of routing due to SQL comment bypass. Got " + count + " rows.");
                }
                assertEquals("Should have routed to db0", 1, count);
            }
        }
    }

    @Test
    public void testSchemaTransformerBypass() throws SQLException {
        SchemaTransformer transformer = new SchemaTransformer("tenant1");
        String sql = "SELECT * FROM /* bypass */ users";
        String transformed = transformer.transformSql(sql);
        assertTrue("SchemaTransformer should have prefixed table name even with comment. Got: " + transformed,
            transformed.contains("tenant1.users"));
    }

    @Test
    public void testWhereTransformerBypass() throws SQLException {
        WhereTransformer transformer = new WhereTransformer("tenant_id=1");
        String sql = "/* bypass */ SELECT * FROM users";
        String transformed = transformer.transformSql(sql);
        assertTrue("WhereTransformer should have added WHERE clause even with leading comment. Got: " + transformed,
            transformed.contains("WHERE tenant_id=1"));
    }
}
