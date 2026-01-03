import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
import org.junit.*;
import org.pjdbc.drivers.*;
import static org.junit.Assert.*;

public class LogDriverTest {
    // Custom handler that collects log messages
    private static class TestHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null && record.getMessage() != null) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}

        public List<String> getMessages() {
            return new ArrayList<>(messages);
        }

        public void clear() {
            messages.clear();
        }
    }

    @Test
    public void acceptsURL () {
	assertFalse(new LogDriver().acceptsURL("jdbc:log"));
	assertFalse(new LogDriver().acceptsURL("jdbc:log:"));
	assertFalse(new LogDriver().acceptsURL("jdbc:log:foo"));
	assertTrue(new LogDriver().acceptsURL("jdbc:log:jdbc:mock:foo"));}

    @Test
    public void testConnectDirectlyAndInvokeMethods () {
	Logger logger = Logger.getLogger("jdbc:mock:foo");
	TestHandler testHandler = new TestHandler();
	Handler[] originalHandlers = logger.getHandlers();
	Level originalLevel = logger.getLevel();
	boolean originalUseParent = logger.getUseParentHandlers();

	try {
	    // Configure logger to capture messages
	    logger.setLevel(Level.INFO);
	    logger.setUseParentHandlers(false);
	    for (Handler h : originalHandlers) logger.removeHandler(h);
	    logger.addHandler(testHandler);

	    Connection c = (new LogDriver().connect("jdbc:log:jdbc:mock:foo", null));
	    Statement stmt = c.createStatement();
	    stmt.executeQuery("select * from person;");
	    stmt.executeQuery("insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);");

	    assertNotNull(MockDriver.getLog("jdbc:mock:foo"));
	    assertEquals("executeQuery[select * from person;]\n" +
			 "executeQuery[insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);]",
			 MockDriver.getLog("jdbc:mock:foo"));

	    List<String> logged = testHandler.getMessages();
	    assertEquals(2, logged.size());
	    assertEquals("select * from person;", logged.get(0));
	    assertEquals("insert into person (last_name, first_name, age) values ('David', 'Ventimiglia', 42);", logged.get(1));
	}
	catch (Exception e) {fail(e.getMessage());}
	finally {
	    // Restore logger state
	    logger.removeHandler(testHandler);
	    for (Handler h : originalHandlers) logger.addHandler(h);
	    logger.setLevel(originalLevel);
	    logger.setUseParentHandlers(originalUseParent);
	}}

    @Test
    public void testConnectDirectly () {
	try {
	    assertFalse(new LogDriver().acceptsURL("foo"));
	    assertNull(new LogDriver().connect("foo", null));
	    assertNull(new LogDriver().connect("jdbc:log", null));
	    assertNull(new LogDriver().connect("jdbc:log:", null));
	    assertNotNull(new LogDriver().connect("jdbc:log:jdbc:mock:foo", null));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void connectIndirectly () {
	try {assertNotNull(DriverManager.getConnection("jdbc:log:jdbc:mock:foo"));}
	catch (Exception e) {fail(e.getMessage());}}

    @Test
    public void compliance () {
	assertFalse(new LogDriver().jdbcCompliant());}

    @Test
    public void versionInfo () {
	assertEquals(1, new LogDriver().getMajorVersion());
	assertEquals(0, new LogDriver().getMinorVersion());}}
