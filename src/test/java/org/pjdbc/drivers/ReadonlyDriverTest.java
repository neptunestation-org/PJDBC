package org.pjdbc.drivers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class ReadonlyDriverTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testAcceptsURL() throws SQLException {
        ReadonlyDriver driver = new ReadonlyDriver();
        assertTrue(driver.acceptsURL("jdbc:readonly:jdbc:h2:mem:test"));
        assertTrue(driver.acceptsURL("jdbc:readonly[allowDDL=true]:jdbc:h2:mem:test"));
        assertFalse(driver.acceptsURL("jdbc:other:jdbc:h2:mem:test"));
    }

    @Test
    public void testBasicConnection() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_basic";
        try (Connection conn = DriverManager.getConnection(url)) {
            assertNotNull(conn);
            try (Statement stmt = conn.createStatement()) {
                assertNotNull(stmt);
            }
        }
    }

    @Test
    public void testSelectAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_select";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testInsertBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_insert";
        try (Connection conn = DriverManager.getConnection(url)) {
            // First create a table using a separate connection
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_insert")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT)");
                }
            }
            // Now try to insert through readonly driver
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO test_table VALUES (1)");
                fail("Expected SQLException for INSERT");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("ReadonlyDriver"));
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testUpdateBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_update";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE some_table SET col = 1");
                fail("Expected SQLException for UPDATE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testDeleteBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_delete";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM some_table");
                fail("Expected SQLException for DELETE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testCreateBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_create";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE new_table (id INT)");
                fail("Expected SQLException for CREATE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DDL blocked"));
        }
    }

    @Test
    public void testDropBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_drop";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE some_table");
                fail("Expected SQLException for DROP");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DDL blocked"));
        }
    }

    @Test
    public void testAlterBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_alter";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE some_table ADD COLUMN new_col INT");
                fail("Expected SQLException for ALTER");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DDL blocked"));
        }
    }

    @Test
    public void testTruncateBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_truncate";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE some_table");
                fail("Expected SQLException for TRUNCATE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testAllowDDL() throws SQLException {
        String url = "jdbc:readonly[allowDDL=true]:jdbc:h2:mem:test_allow_ddl";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                // DDL should be allowed
                stmt.execute("CREATE TABLE IF NOT EXISTS ddl_test (id INT)");
                stmt.execute("DROP TABLE IF EXISTS ddl_test");
            }
            // But DML should still be blocked
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO ddl_test VALUES (1)");
                fail("Expected SQLException for INSERT even with allowDDL=true");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testAllowDML() throws SQLException {
        String url = "jdbc:readonly[allowDML=true]:jdbc:h2:mem:test_allow_dml";
        try (Connection conn = DriverManager.getConnection(url)) {
            // First need to create table (DDL still blocked by default)
            try (Connection setupConn = DriverManager.getConnection("jdbc:h2:mem:test_allow_dml")) {
                try (Statement stmt = setupConn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS dml_test (id INT)");
                }
            }
            // Now DML should work
            try (Statement stmt = conn.createStatement()) {
                int rows = stmt.executeUpdate("INSERT INTO dml_test VALUES (1)");
                assertEquals(1, rows);
            }
            // But DDL should still be blocked
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE dml_test");
                fail("Expected SQLException for DROP");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DDL blocked"));
        }
    }

    @Test
    public void testPreparedStatementBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_prepared";
        try (Connection conn = DriverManager.getConnection(url)) {
            // Prepare should fail for write operations
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO test VALUES (?)")) {
                fail("Expected SQLException at prepare time for INSERT");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testPreparedStatementSelectAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_prepared_select";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT ?")) {
                pstmt.setInt(1, 42);
                try (ResultSet rs = pstmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt(1));
                }
            }
        }
    }

    @Test
    public void testCustomMessage() throws SQLException {
        String url = "jdbc:readonly[message=Custom readonly error]:jdbc:h2:mem:test_message";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO test VALUES (1)");
                fail("Expected SQLException");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("Custom readonly error"));
        }
    }

    @Test
    public void testConfigDefaults() {
        ReadonlyDriver.ReadonlyConfig config = new ReadonlyDriver.ReadonlyConfig("jdbc:readonly:jdbc:h2:mem:test");
        assertFalse(config.isAllowDDL());
        assertFalse(config.isAllowDML());
        assertEquals("ReadonlyDriver: Write operation not permitted", config.getMessage());
    }

    @Test
    public void testConfigCustomValues() {
        ReadonlyDriver.ReadonlyConfig config = new ReadonlyDriver.ReadonlyConfig(
            "jdbc:readonly[allowDDL=true,allowDML=true,message=Custom]:jdbc:h2:mem:test"
        );
        assertTrue(config.isAllowDDL());
        assertTrue(config.isAllowDML());
        assertEquals("Custom", config.getMessage());
    }

    @Test
    public void testDriverChaining() throws SQLException, ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.LogDriver");
        String url = "jdbc:readonly:jdbc:log:jdbc:h2:mem:test_chain";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT 'chained'")) {
                    assertTrue(rs.next());
                    assertEquals("chained", rs.getString(1));
                }
            }
        }
    }

    @Test
    public void testCaseInsensitive() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_case";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("insert into test values (1)");
                fail("Expected SQLException for lowercase INSERT");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testWhitespaceHandling() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_whitespace";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("   INSERT INTO test VALUES (1)");
                fail("Expected SQLException for INSERT with leading whitespace");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testMergeBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_merge";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("MERGE INTO test KEY(id) VALUES (1)");
                fail("Expected SQLException for MERGE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DML blocked"));
        }
    }

    @Test
    public void testGrantBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_grant";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("GRANT SELECT ON test TO user1");
                fail("Expected SQLException for GRANT");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DCL blocked"));
        }
    }

    @Test
    public void testRevokeBlocked() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_revoke";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("REVOKE SELECT ON test FROM user1");
                fail("Expected SQLException for REVOKE");
            }
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("DCL blocked"));
        }
    }

    @Test
    public void testMultipleSelectsAllowed() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_multi";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                for (int i = 0; i < 5; i++) {
                    try (ResultSet rs = stmt.executeQuery("SELECT " + i)) {
                        assertTrue(rs.next());
                        assertEquals(i, rs.getInt(1));
                    }
                }
            }
        }
    }
}
