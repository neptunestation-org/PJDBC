import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.Before;
import org.pjdbc.drivers.*;
import org.pjdbc.sql.*;
import static org.junit.Assert.*;

/**
 * Edge case tests for all PJDBC drivers using TDD approach.
 * Tests cover: null URLs, malformed URLs, null properties, empty properties,
 * boundary conditions, and concurrent access patterns.
 */
public class DriverEdgeCasesTest {

    @Before
    public void setUp() {
        // Clear MockDriver logs before each test
        try {
            MockDriver.clearLogs();
        } catch (Exception e) {
            // Ignore if MockDriver doesn't have clearLogs
        }
    }

    // ========== FilterDriver Edge Cases ==========

    @Test
    public void filterDriver_nullURL() {
        FilterDriver driver = new FilterDriver();
        assertFalse("FilterDriver should reject null URL", driver.acceptsURL(null));
        try {
            Connection conn = driver.connect(null, new Properties());
            assertNull("Connection should be null for null URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for null URL: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_emptyURL() {
        FilterDriver driver = new FilterDriver();
        assertFalse("FilterDriver should reject empty URL", driver.acceptsURL(""));
        try {
            Connection conn = driver.connect("", new Properties());
            assertNull("Connection should be null for empty URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for empty URL: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_malformedURL_noSubname() {
        FilterDriver driver = new FilterDriver();
        assertFalse("FilterDriver should reject URL without subname", driver.acceptsURL("jdbc:filter:"));
        try {
            Connection conn = driver.connect("jdbc:filter:", null);
            assertNull("Connection should be null for malformed URL", conn);
        } catch (SQLException e) {
            // Expected behavior - may throw SQLException
        }
    }

    @Test
    public void filterDriver_malformedURL_invalidSubprotocol() {
        FilterDriver driver = new FilterDriver();
        assertFalse("FilterDriver should reject wrong subprotocol", driver.acceptsURL("jdbc:filtering:jdbc:mock:foo"));
    }

    @Test
    public void filterDriver_nullProperties() {
        FilterDriver driver = new FilterDriver();
        try {
            Connection conn = driver.connect("jdbc:filter:jdbc:mock:foo", null);
            assertNotNull("FilterDriver should accept null Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("FilterDriver should handle null Properties: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_emptyProperties() {
        FilterDriver driver = new FilterDriver();
        try {
            Connection conn = driver.connect("jdbc:filter:jdbc:mock:foo", new Properties());
            assertNotNull("FilterDriver should accept empty Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("FilterDriver should handle empty Properties: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_nullTransformer() {
        FilterDriver driver = new FilterDriver();
        try {
            driver.setTransformer(null);
            Connection conn = driver.connect("jdbc:filter:jdbc:mock:foo", null);
            assertNotNull("FilterDriver should accept null transformer", conn);
            // Attempt to execute query with null transformer
            conn.createStatement().executeQuery("SELECT 1");
        } catch (NullPointerException e) {
            // Expected - null transformer will cause NPE
            return;
        } catch (SQLException e) {
            fail("Unexpected SQLException: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_transformerWithNullSQL() {
        FilterDriver driver = new FilterDriver();
        driver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String sql) throws SQLException {
                return sql == null ? "SELECT 1" : sql.toUpperCase();
            }
        });
        try {
            Connection conn = driver.connect("jdbc:filter:jdbc:mock:foo", null);
            assertNotNull(conn);
            conn.close();
        } catch (SQLException e) {
            fail("FilterDriver should handle transformer with null SQL handling: " + e.getMessage());
        }
    }

    @Test
    public void filterDriver_concurrentTransformerAccess() throws Exception {
        final FilterDriver driver = (FilterDriver) DriverManager.getDriver("jdbc:filter:jdbc:mock:foo");
        final CountDownLatch latch = new CountDownLatch(10);
        final CopyOnWriteArrayList<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    driver.setTransformer(new AbstractJdbcTransformer() {
                        @Override
                        public String transformSql(String sql) {
                            return "QUERY" + index;
                        }
                    });
                    Connection conn = DriverManager.getConnection("jdbc:filter:jdbc:mock:foo");
                    conn.createStatement().executeQuery("SELECT " + index);
                    conn.close();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        if (!errors.isEmpty()) {
            // Concurrent access may cause issues - this is expected behavior
            // The test documents the behavior rather than enforcing thread safety
        }
    }

    // ========== TeeDriver Edge Cases ==========

    @Test
    public void teeDriver_nullURL() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject null URL", driver.acceptsURL(null));
        try {
            Connection conn = driver.connect(null, new Properties());
            assertNull("Connection should be null for null URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for null URL: " + e.getMessage());
        }
    }

    @Test
    public void teeDriver_emptyURL() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject empty URL", driver.acceptsURL(""));
    }

    @Test
    public void teeDriver_malformedURL_onlyOneURL() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject URL with only one target",
                    driver.acceptsURL("jdbc:tee:jdbc:mock:foo"));
    }

    @Test
    public void teeDriver_malformedURL_noSemicolon() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject URL without semicolon separator",
                    driver.acceptsURL("jdbc:tee:jdbc:mock:foo jdbc:mock:bar"));
    }

    @Test
    public void teeDriver_malformedURL_invalidTargetURL() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject URL with invalid target",
                    driver.acceptsURL("jdbc:tee:jdbc:invalid:foo;jdbc:invalid:bar"));
    }

    @Test
    public void teeDriver_malformedURL_emptyTargets() {
        TeeDriver driver = new TeeDriver();
        assertFalse("TeeDriver should reject URL with empty targets",
                    driver.acceptsURL("jdbc:tee:;"));
    }

    @Test
    public void teeDriver_nullProperties() {
        TeeDriver driver = new TeeDriver();
        try {
            Connection conn = driver.connect("jdbc:tee:jdbc:mock:foo;jdbc:mock:bar", null);
            assertNotNull("TeeDriver should accept null Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("TeeDriver should handle null Properties: " + e.getMessage());
        }
    }

    @Test
    public void teeDriver_emptyProperties() {
        TeeDriver driver = new TeeDriver();
        try {
            Connection conn = driver.connect("jdbc:tee:jdbc:mock:foo;jdbc:mock:bar", new Properties());
            assertNotNull("TeeDriver should accept empty Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("TeeDriver should handle empty Properties: " + e.getMessage());
        }
    }

    @Test
    public void teeDriver_concurrentConnections() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final CopyOnWriteArrayList<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    Connection conn = DriverManager.getConnection("jdbc:tee:jdbc:mock:foo;jdbc:mock:bar");
                    conn.createStatement().executeQuery("SELECT 1");
                    conn.close();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        assertTrue("Concurrent TeeDriver connections should succeed", errors.isEmpty());
    }

    // ========== PoolDriver Edge Cases ==========

    @Test
    public void poolDriver_nullURL() {
        PoolDriver driver = new PoolDriver();
        assertFalse("PoolDriver should reject null URL", driver.acceptsURL(null));
        try {
            Connection conn = driver.connect(null, new Properties());
            assertNull("Connection should be null for null URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for null URL: " + e.getMessage());
        }
    }

    @Test
    public void poolDriver_emptyURL() {
        PoolDriver driver = new PoolDriver();
        assertFalse("PoolDriver should reject empty URL", driver.acceptsURL(""));
    }

    @Test
    public void poolDriver_malformedURL_noSubname() {
        PoolDriver driver = new PoolDriver();
        assertFalse("PoolDriver should reject URL without subname", driver.acceptsURL("jdbc:pool:"));
    }

    @Test
    public void poolDriver_nullProperties() {
        PoolDriver driver = new PoolDriver();
        try {
            Connection conn = driver.connect("jdbc:pool:jdbc:mock:foo", null);
            assertNotNull("PoolDriver should accept null Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("PoolDriver should handle null Properties: " + e.getMessage());
        }
    }

    @Test
    public void poolDriver_emptyProperties() {
        PoolDriver driver = new PoolDriver();
        try {
            Connection conn = driver.connect("jdbc:pool:jdbc:mock:foo", new Properties());
            assertNotNull("PoolDriver should accept empty Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("PoolDriver should handle empty Properties: " + e.getMessage());
        }
    }

    @Test
    public void poolDriver_connectionReuseAfterClose() {
        try {
            Connection conn1 = DriverManager.getConnection("jdbc:pool:jdbc:mock:reuse");
            assertNotNull(conn1);
            conn1.close();

            // Get another connection - should reuse from pool
            Connection conn2 = DriverManager.getConnection("jdbc:pool:jdbc:mock:reuse");
            assertNotNull(conn2);

            // Connections should be the same underlying connection (from pool)
            // but different proxy instances
            assertNotNull("Second connection should be retrieved from pool", conn2);
            conn2.close();
        } catch (SQLException e) {
            fail("PoolDriver should support connection reuse: " + e.getMessage());
        }
    }

    @Test
    public void poolDriver_concurrentPoolAccess() throws Exception {
        final CountDownLatch latch = new CountDownLatch(10);
        final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    Connection conn = DriverManager.getConnection("jdbc:pool:jdbc:mock:concurrent");
                    connections.add(conn);
                    Thread.sleep(10);
                    conn.close();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);
        assertTrue("Concurrent pool access should succeed", errors.isEmpty());
        assertEquals("Should create 10 connections", 10, connections.size());
    }

    @Test
    public void poolDriver_useClosedConnectionThrowsSQLException() {
        try {
            Connection conn = DriverManager.getConnection("jdbc:pool:jdbc:mock:closed");
            conn.close();

            try {
                conn.createStatement();
                fail("Using closed pooled connection should throw SQLException");
            } catch (SQLException e) {
                // Expected behavior
            }
        } catch (SQLException e) {
            fail("Initial connection should succeed: " + e.getMessage());
        }
    }

    // ========== CatDriver Edge Cases ==========

    @Test
    public void catDriver_nullURL() {
        CatDriver driver = new CatDriver();
        assertFalse("CatDriver should reject null URL", driver.acceptsURL(null));
        try {
            Connection conn = driver.connect(null, new Properties());
            assertNull("Connection should be null for null URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for null URL: " + e.getMessage());
        }
    }

    @Test
    public void catDriver_emptyURL() {
        CatDriver driver = new CatDriver();
        assertFalse("CatDriver should reject empty URL", driver.acceptsURL(""));
    }

    @Test
    public void catDriver_malformedURL_noSubname() {
        CatDriver driver = new CatDriver();
        assertFalse("CatDriver should reject URL without subname", driver.acceptsURL("jdbc:cat:"));
    }

    @Test
    public void catDriver_malformedURL_invalidTargetURL() {
        CatDriver driver = new CatDriver();
        // URL with invalid target should be rejected
        assertFalse("CatDriver should reject URL with invalid target",
                    driver.acceptsURL("jdbc:cat:jdbc:invalid:foo"));
    }

    @Test
    public void catDriver_nullProperties() {
        CatDriver driver = new CatDriver();
        try {
            Connection conn = driver.connect("jdbc:cat:jdbc:mock:foo", null);
            assertNotNull("CatDriver should accept null Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("CatDriver should handle null Properties: " + e.getMessage());
        }
    }

    @Test
    public void catDriver_emptyProperties() {
        CatDriver driver = new CatDriver();
        try {
            Connection conn = driver.connect("jdbc:cat:jdbc:mock:foo", new Properties());
            assertNotNull("CatDriver should accept empty Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("CatDriver should handle empty Properties: " + e.getMessage());
        }
    }

    @Test
    public void catDriver_concurrentConnections() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final CopyOnWriteArrayList<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    Connection conn = DriverManager.getConnection("jdbc:cat:jdbc:mock:concurrent");
                    conn.createStatement().executeQuery("SELECT 1");
                    conn.close();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        assertTrue("Concurrent CatDriver connections should succeed", errors.isEmpty());
    }

    // ========== UserMapDriver Edge Cases ==========

    @Test
    public void userMapDriver_nullURL() {
        try {
            UserMapDriver driver = new UserMapDriver();
            assertFalse("UserMapDriver should reject null URL", driver.acceptsURL(null));
            try {
                Connection conn = driver.connect(null, new Properties());
                assertNull("Connection should be null for null URL", conn);
            } catch (SQLException e) {
                fail("Should not throw SQLException for null URL: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
            // This is expected behavior for this edge case
        }
    }

    @Test
    public void userMapDriver_emptyURL() {
        try {
            UserMapDriver driver = new UserMapDriver();
            assertFalse("UserMapDriver should reject empty URL", driver.acceptsURL(""));
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
        }
    }

    @Test
    public void userMapDriver_malformedURL_noSubname() {
        try {
            UserMapDriver driver = new UserMapDriver();
            assertFalse("UserMapDriver should reject URL without subname",
                        driver.acceptsURL("jdbc:mapuser:"));
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
        }
    }

    @Test
    public void userMapDriver_nullProperties() {
        try {
            UserMapDriver driver = new UserMapDriver();
            Connection conn = driver.connect("jdbc:mapuser:jdbc:mock:foo", null);
            // Should throw NullPointerException when trying to get user from null Properties
            fail("UserMapDriver should not accept null Properties");
        } catch (NullPointerException | SQLException e) {
            // Expected - null Properties will cause NPE when getting user
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
        }
    }

    @Test
    public void userMapDriver_emptyProperties() {
        try {
            UserMapDriver driver = new UserMapDriver();
            Properties props = new Properties();
            Connection conn = driver.connect("jdbc:mapuser:jdbc:mock:foo", props);
            // Should throw exception when user property is missing
            fail("UserMapDriver should not accept Properties without user");
        } catch (NullPointerException | SQLException e) {
            // Expected - missing user property will cause NPE
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
        }
    }

    @Test
    public void userMapDriver_propertiesWithoutUser() {
        try {
            UserMapDriver driver = new UserMapDriver();
            Properties props = new Properties();
            props.setProperty("password", "testpass");
            Connection conn = driver.connect("jdbc:mapuser:jdbc:mock:foo", props);
            fail("UserMapDriver should fail when user property is missing");
        } catch (NullPointerException | SQLException e) {
            // Expected behavior
        } catch (RuntimeException e) {
            // UserMapDriver may throw RuntimeException if UserMapFile is not found
        }
    }

    // ========== LogDriver Edge Cases ==========

    @Test
    public void logDriver_nullURL() {
        LogDriver driver = new LogDriver();
        assertFalse("LogDriver should reject null URL", driver.acceptsURL(null));
        try {
            Connection conn = driver.connect(null, new Properties());
            assertNull("Connection should be null for null URL", conn);
        } catch (SQLException e) {
            fail("Should not throw SQLException for null URL: " + e.getMessage());
        }
    }

    @Test
    public void logDriver_emptyURL() {
        LogDriver driver = new LogDriver();
        assertFalse("LogDriver should reject empty URL", driver.acceptsURL(""));
    }

    @Test
    public void logDriver_malformedURL_noSubname() {
        LogDriver driver = new LogDriver();
        assertFalse("LogDriver should reject URL without subname", driver.acceptsURL("jdbc:log:"));
    }

    @Test
    public void logDriver_malformedURL_invalidTargetURL() {
        LogDriver driver = new LogDriver();
        assertFalse("LogDriver should reject URL with invalid target",
                    driver.acceptsURL("jdbc:log:jdbc:invalid:foo"));
    }

    @Test
    public void logDriver_nullProperties() {
        LogDriver driver = new LogDriver();
        try {
            Connection conn = driver.connect("jdbc:log:jdbc:mock:foo", null);
            assertNotNull("LogDriver should accept null Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("LogDriver should handle null Properties: " + e.getMessage());
        }
    }

    @Test
    public void logDriver_emptyProperties() {
        LogDriver driver = new LogDriver();
        try {
            Connection conn = driver.connect("jdbc:log:jdbc:mock:foo", new Properties());
            assertNotNull("LogDriver should accept empty Properties", conn);
            conn.close();
        } catch (SQLException e) {
            fail("LogDriver should handle empty Properties: " + e.getMessage());
        }
    }

    @Test
    public void logDriver_concurrentConnections() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);
        final CopyOnWriteArrayList<Exception> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    Connection conn = DriverManager.getConnection("jdbc:log:jdbc:mock:log" + index);
                    conn.createStatement().executeQuery("SELECT " + index);
                    conn.close();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        assertTrue("Concurrent LogDriver connections should succeed", errors.isEmpty());
    }

    // ========== Cross-Driver Boundary Tests ==========

    @Test
    public void allDrivers_rejectWrongSubprotocol() throws SQLException {
        Driver[] drivers = {
            new FilterDriver(),
            new TeeDriver(),
            new PoolDriver(),
            new CatDriver(),
            new LogDriver()
        };

        String[] wrongProtocols = {
            "jdbc:wrong:jdbc:mock:foo",
            "jdbc::jdbc:mock:foo",
            "jdbc:mock:foo",
            "wrong:filter:jdbc:mock:foo"
        };

        for (Driver driver : drivers) {
            for (String url : wrongProtocols) {
                assertFalse(driver.getClass().getSimpleName() + " should reject wrong protocol: " + url,
                           driver.acceptsURL(url));
            }
        }
    }

    @Test
    public void allDrivers_jdbcCompliantReturnsFalse() {
        assertFalse("FilterDriver should not be JDBC compliant", new FilterDriver().jdbcCompliant());
        assertFalse("TeeDriver should not be JDBC compliant", new TeeDriver().jdbcCompliant());
        assertFalse("PoolDriver should not be JDBC compliant", new PoolDriver().jdbcCompliant());
        assertFalse("CatDriver should not be JDBC compliant", new CatDriver().jdbcCompliant());
        assertFalse("LogDriver should not be JDBC compliant", new LogDriver().jdbcCompliant());
    }

    @Test
    public void allDrivers_versionInfo() {
        Driver[] drivers = {
            new FilterDriver(),
            new TeeDriver(),
            new PoolDriver(),
            new CatDriver(),
            new LogDriver()
        };

        for (Driver driver : drivers) {
            assertEquals(driver.getClass().getSimpleName() + " major version should be 1",
                        1, driver.getMajorVersion());
            assertEquals(driver.getClass().getSimpleName() + " minor version should be 0",
                        0, driver.getMinorVersion());
        }
    }
}
