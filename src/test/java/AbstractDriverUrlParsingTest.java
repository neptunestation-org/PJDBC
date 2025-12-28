import org.junit.Test;
import org.pjdbc.sql.AbstractDriver;
import static org.junit.Assert.*;

import java.sql.*;
import java.util.*;

/**
 * Tests for AbstractDriver URL parsing using JdbcUrlParser.
 */
public class AbstractDriverUrlParsingTest {

    // Concrete test implementation of AbstractDriver
    static class TestDriver extends AbstractDriver {
        @Override
        protected boolean acceptsSubProtocol(String subprotocol) {
            return "test".equals(subprotocol);
        }

        @Override
        protected boolean acceptsSubName(String subname) {
            return subname != null && subname.startsWith("jdbc:");
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return null; // Not needed for URL parsing tests
        }

        // Expose protected methods for testing
        public String testProtocol(String url) { return protocol(url); }
        public String testSubprotocol(String url) { return subprotocol(url); }
        public String testSubname(String url) { return subname(url); }

        // New method to test - should use JdbcUrlParser
        public String testGetUrlParameter(String url, String key) {
            return getUrlParameter(url, key);
        }
        public String testGetUrlParameter(String url, String key, String defaultValue) {
            return getUrlParameter(url, key, defaultValue);
        }
    }

    private TestDriver driver = new TestDriver();

    @Test
    public void parseSimpleUrl() {
        String url = "jdbc:test:jdbc:mock:foo";
        assertEquals("jdbc", driver.testProtocol(url));
        assertEquals("test", driver.testSubprotocol(url));
        assertEquals("jdbc:mock:foo", driver.testSubname(url));
    }

    @Test
    public void parseUrlWithParameters() {
        String url = "jdbc:test[timeout=30]:jdbc:mock:foo";
        assertEquals("jdbc", driver.testProtocol(url));
        assertEquals("test", driver.testSubprotocol(url));
        assertEquals("jdbc:mock:foo", driver.testSubname(url));
    }

    @Test
    public void getUrlParameter() {
        String url = "jdbc:test[timeout=30,debug=true]:jdbc:mock:foo";
        assertEquals("30", driver.testGetUrlParameter(url, "timeout"));
        assertEquals("true", driver.testGetUrlParameter(url, "debug"));
        assertNull(driver.testGetUrlParameter(url, "nonexistent"));
    }

    @Test
    public void getUrlParameterWithDefault() {
        String url = "jdbc:test[timeout=30]:jdbc:mock:foo";
        assertEquals("30", driver.testGetUrlParameter(url, "timeout", "10"));
        assertEquals("10", driver.testGetUrlParameter(url, "retries", "10"));
    }

    @Test
    public void acceptsUrlWithParameters() {
        assertTrue(driver.acceptsURL("jdbc:test[timeout=30]:jdbc:mock:foo"));
        assertTrue(driver.acceptsURL("jdbc:test[a=1,b=2]:jdbc:mock:foo"));
    }

    @Test
    public void acceptsUrlWithEmptyParameters() {
        assertTrue(driver.acceptsURL("jdbc:test[]:jdbc:mock:foo"));
    }

    @Test
    public void subprotocolStripsParameters() {
        // subprotocol should return just "test", not "test[timeout=30]"
        String url = "jdbc:test[timeout=30]:jdbc:mock:foo";
        assertEquals("test", driver.testSubprotocol(url));
    }
}
