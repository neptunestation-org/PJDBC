import java.sql.*;
import java.util.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class UserMapDriverTest {

    @Before
    public void setUp() throws SQLException {
        // Ensure the mock driver is registered for the test
        DriverManager.registerDriver(new MockDriver());
    }

    @Test
    public void acceptsURL () {
	// Should reject invalid URLs
	assertFalse(new UserMapDriver().acceptsURL("jdbc:mapuser"));
	assertFalse(new UserMapDriver().acceptsURL("jdbc:mapuser:"));
	assertFalse(new UserMapDriver().acceptsURL("jdbc:mapping:jdbc:mock:foo"));
	assertFalse(new UserMapDriver().acceptsURL("foo"));

	// Should accept valid mapuser URLs
	assertTrue(new UserMapDriver().acceptsURL("jdbc:mapuser:jdbc:mock:foo"));
	assertTrue(new UserMapDriver().acceptsURL("jdbc:mapuser:jdbc:mock:testdb"));}

    @Test
    public void connectDirectly () {
	try {
	    // Should reject invalid URLs
	    assertFalse(new UserMapDriver().acceptsURL("foo"));
	    assertNull(new UserMapDriver().connect("foo", null));
	    assertNull(new UserMapDriver().connect("jdbc:mapuser", null));
	    assertNull(new UserMapDriver().connect("jdbc:mapuser:", null));

	    // Should accept valid URL with mapped user
	    Properties props = new Properties();
	    props.setProperty("user", "alice");
	    assertNotNull(new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectIndirectly () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "bob");
	    assertNotNull(DriverManager.getConnection("jdbc:mapuser:jdbc:mock:testdb", props));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void usernameMappingApplied () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "alice");

	    // Connect with alice, should be mapped to alice_db/secret123
	    Connection c = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
	    assertNotNull(c);
            Properties mockInfo = MockDriver.getLastConnectionInfo("jdbc:mock:testdb");
            assertEquals("alice_db", mockInfo.getProperty("user"));
            assertEquals("secret123", mockInfo.getProperty("password"));
        }
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void differentUserMappings () {
	try {
	    // Test multiple different user mappings
	    Properties props1 = new Properties();
	    props1.setProperty("user", "alice");
	    Connection c1 = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:db1", props1);
	    assertNotNull(c1);

	    Properties props2 = new Properties();
	    props2.setProperty("user", "bob");
	    Connection c2 = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:db2", props2);
	    assertNotNull(c2);

	    Properties props3 = new Properties();
	    props3.setProperty("user", "testuser");
	    Connection c3 = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:db3", props3);
	    assertNotNull(c3);}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void adminUserMapping () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "admin");
	    Connection c = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:admindb", props);
	    assertNotNull(c);
            Properties mockInfo = MockDriver.getLastConnectionInfo("jdbc:mock:admindb");
            assertEquals("admin_db", mockInfo.getProperty("user"));
            assertEquals("admin_secret", mockInfo.getProperty("password"));
        }
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void missingUserMappingThrowsException () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "unknownuser");
	    new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
	    fail("Should throw exception for unmapped user");}
	catch (SQLException e) {
            assertEquals("PJDBC: Authentication failed", e.getMessage());
	}
	catch (Exception e) {
            fail("Threw wrong exception type for missing user");
        }
    }

    @Test
    public void testNullPropertiesThrowsSecureException() {
        try {
            new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", null);
            fail("Should throw exception for null properties");
        } catch (SQLException e) {
            assertEquals("PJDBC: Authentication failed", e.getMessage());
        } catch (Exception e) {
            fail("Threw wrong exception type for null properties");
        }
    }

    @Test
    public void nullUserThrowsException () {
	try {
	    Properties props = new Properties();
	    new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
	    fail("Should throw exception for null user");}
	catch (SQLException e) {
            assertEquals("PJDBC: Authentication failed", e.getMessage());
	}
	catch (Exception e) {
            fail("Threw wrong exception type for null user");
        }
    }

    @Test
    public void malformedUserMappingThrowsException() {
        try {
            Properties props = new Properties();
            props.setProperty("user", "malformed_user");
            new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
            fail("Should throw exception for malformed user");
        } catch (SQLException e) {
            assertEquals("PJDBC: Authentication failed", e.getMessage());
        } catch (Exception e) {
            fail("Threw wrong exception type for malformed user");
        }
    }

    @Test
    public void whitespaceUserMapping() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", "whitespace_user");
        Connection c = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:ws_db", props);
        assertNotNull(c);
        Properties mockInfo = MockDriver.getLastConnectionInfo("jdbc:mock:ws_db");
        assertEquals("db_user_ws", mockInfo.getProperty("user"));
        assertEquals("db_pass_ws", mockInfo.getProperty("password"));
    }

    @Test
    public void compliance () {
	assertFalse(new UserMapDriver().jdbcCompliant());}

    @Test
    public void versionInfo () {
	assertEquals(1, new UserMapDriver().getMajorVersion());
	assertEquals(0, new UserMapDriver().getMinorVersion());}

    @Test
    public void connectWithOriginalPassword () {
	try {
	    // Even if original password is provided, it should be overridden
	    Properties props = new Properties();
	    props.setProperty("user", "alice");
	    props.setProperty("password", "wrongpassword");

	    // Should still connect successfully with mapped credentials
	    Connection c = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb2", props);
	    assertNotNull(c);
            Properties mockInfo = MockDriver.getLastConnectionInfo("jdbc:mock:testdb2");
            assertEquals("alice_db", mockInfo.getProperty("user"));
            assertEquals("secret123", mockInfo.getProperty("password"));
        }
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void multipleConnectionsSameUser () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "alice");

	    Connection c1 = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:db1", props);
	    Connection c2 = new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:db2", props);

	    assertNotNull(c1);
	    assertNotNull(c2);
	    assertNotSame(c1, c2);}
	catch (Exception e) {fail(e.getMessage());}}
}
