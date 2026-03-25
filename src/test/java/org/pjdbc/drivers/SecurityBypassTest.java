package org.pjdbc.drivers;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.BeforeClass;
import org.junit.Test;

public class SecurityBypassTest {

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.pjdbc.drivers.ReadonlyDriver");
    }

    @Test
    public void testReadonlyBlockCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_block_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("/* comment */ CREATE TABLE bypass1 (id INT)");
                fail("ReadonlyDriver was bypassed using a leading block comment!");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message", e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testReadonlyLineCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_line_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("-- line comment\nCREATE TABLE bypass2 (id INT)");
                fail("ReadonlyDriver was bypassed using a leading line comment!");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message", e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }

    @Test
    public void testReadonlyNestedCommentBypass() throws SQLException {
        String url = "jdbc:readonly:jdbc:h2:mem:test_readonly_nested_bypass";
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("  /* block */ -- line\n  /* another */ INSERT INTO test VALUES (1)");
                fail("ReadonlyDriver was bypassed using nested leading comments!");
            } catch (SQLException e) {
                assertTrue("Expected ReadonlyDriver in error message", e.getMessage().contains("ReadonlyDriver"));
            }
        }
    }
}
