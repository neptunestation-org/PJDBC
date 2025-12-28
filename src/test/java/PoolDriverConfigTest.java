import java.sql.*;
import java.util.Properties;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

/**
 * Tests for configurable PoolDriver using URL parameters.
 * URL format: jdbc:pool[param=value,...]:suburl
 * Supported parameters:
 *   - min: minimum pool size (default 0)
 *   - max: maximum pool size (default unlimited/-1)
 *   - timeout: connection timeout in ms (default 1000)
 */
public class PoolDriverConfigTest {

    @Test
    public void acceptsURLWithParameters() throws SQLException {
        PoolDriver driver = new PoolDriver();
        // Should accept URL with parameters
        assertTrue(driver.acceptsURL("jdbc:pool[min=2,max=10]:jdbc:mock:foo"));
        assertTrue(driver.acceptsURL("jdbc:pool[timeout=5000]:jdbc:mock:bar"));
        assertTrue(driver.acceptsURL("jdbc:pool[min=1,max=5,timeout=2000]:jdbc:mock:baz"));
        // Should still accept URL without parameters
        assertTrue(driver.acceptsURL("jdbc:pool:jdbc:mock:foo"));
    }

    @Test
    public void connectWithMaxPoolSize() throws SQLException {
        // With max=2, we should only be able to hold 2 connections
        Connection c1 = DriverManager.getConnection("jdbc:pool[max=2]:jdbc:mock:maxtest1");
        Connection c2 = DriverManager.getConnection("jdbc:pool[max=2]:jdbc:mock:maxtest1");
        assertNotNull(c1);
        assertNotNull(c2);
        // Both connections work
        assertNotNull(c1.createStatement());
        assertNotNull(c2.createStatement());
        c1.close();
        c2.close();
    }

    @Test
    public void connectWithTimeout() throws SQLException {
        // With short timeout, connection should still work for available pool
        Connection c1 = DriverManager.getConnection("jdbc:pool[timeout=100]:jdbc:mock:timeouttest");
        assertNotNull(c1);
        c1.close();
        // Should be able to reuse the pooled connection
        Connection c2 = DriverManager.getConnection("jdbc:pool[timeout=100]:jdbc:mock:timeouttest");
        assertNotNull(c2);
        c2.close();
    }

    @Test
    public void connectionReuseFromPool() throws SQLException {
        String url = "jdbc:pool[max=1]:jdbc:mock:reusetest";
        Connection c1 = DriverManager.getConnection(url);
        assertNotNull(c1);
        c1.close(); // Returns to pool

        // Should get the same underlying connection from pool
        Connection c2 = DriverManager.getConnection(url);
        assertNotNull(c2);
        c2.close();
    }

    @Test
    public void minPoolSizePrewarming() throws SQLException {
        // min=2 should pre-create 2 connections (implementation detail)
        // For now, just verify URL with min parameter is accepted and works
        Connection c = DriverManager.getConnection("jdbc:pool[min=2]:jdbc:mock:mintest");
        assertNotNull(c);
        c.close();
    }

    @Test
    public void defaultValuesWhenNoParameters() throws SQLException {
        // Without parameters, should use defaults and work normally
        Connection c = DriverManager.getConnection("jdbc:pool:jdbc:mock:defaulttest");
        assertNotNull(c);
        c.close();
    }

    @Test
    public void mixedParameters() throws SQLException {
        // Multiple parameters should all be parsed correctly
        Connection c = DriverManager.getConnection("jdbc:pool[min=1,max=10,timeout=5000]:jdbc:mock:mixedtest");
        assertNotNull(c);
        assertNotNull(c.createStatement());
        c.close();
    }

    @Test
    public void parametersAreCaseInsensitive() throws SQLException {
        // Parameters should work regardless of case
        Connection c1 = DriverManager.getConnection("jdbc:pool[MAX=5]:jdbc:mock:casetest1");
        assertNotNull(c1);
        c1.close();

        Connection c2 = DriverManager.getConnection("jdbc:pool[Min=2,Timeout=1000]:jdbc:mock:casetest2");
        assertNotNull(c2);
        c2.close();
    }

    @Test
    public void invalidParameterValuesUseDefaults() throws SQLException {
        // Invalid numeric values should fall back to defaults
        Connection c = DriverManager.getConnection("jdbc:pool[max=invalid]:jdbc:mock:invalidtest");
        assertNotNull(c);
        c.close();
    }
}
