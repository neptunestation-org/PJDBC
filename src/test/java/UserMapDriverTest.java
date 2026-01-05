import java.sql.*;
import java.util.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class UserMapDriverTest {

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

	    // Verify the connection was made to the underlying driver
	    // The MockDriver should have received the mapped credentials
	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from users");
	    assertNotNull(MockDriver.getLog("jdbc:mock:testdb"));
	    assertEquals("executeQuery[select * from users]", MockDriver.getLog("jdbc:mock:testdb"));}
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

	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from system");
	    assertEquals("executeQuery[select * from system]", MockDriver.getLog("jdbc:mock:admindb"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void missingUserMappingThrowsException () {
	try {
	    Properties props = new Properties();
	    props.setProperty("user", "unknownuser");
	    new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
	    fail("Should throw exception for unmapped user");}
	catch (SQLException e) {
	    // Expected for unmapped user.
	    assertTrue(e.getMessage().contains("No mapping found for user: unknownuser"));
	}
	catch (Exception e) {
	    fail("Expected SQLException for unmapped user, but got " + e.getClass().getName());
	}}

    @Test
    public void nullUserThrowsException () {
	try {
	    Properties props = new Properties();
	    new UserMapDriver().connect("jdbc:mapuser:jdbc:mock:testdb", props);
	    fail("Should throw exception for null user");}
	catch (SQLException e) {
	    // Expected - no user specified
	    assertTrue(e.getMessage().contains("User not specified"));
	}
	catch (Exception e) {
	    fail("Expected SQLException for null user, but got " + e.getClass().getName());
	}}

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
	    assertNotNull(c);}
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
