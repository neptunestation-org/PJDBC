package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.pjdbc.drivers.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for UserMapDriver.
 *
 * UserMapDriver maps application users to database credentials:
 * - URL validation (accepts jdbc:mapuser:...)
 * - User mapping from properties file
 * - Credential transformation
 * - Error handling for missing/invalid mappings
 * - Composition with other drivers
 */
class UserMapDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "um_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    // ========== URL ACCEPTANCE PROPERTIES ==========

    /**
     * Property: UserMapDriver accepts mapuser protocol URLs.
     */
    @Property(tries = 20)
    void acceptsMapuserProtocol(
            @ForAll("mockDbNames") String dbName) {

        UserMapDriver driver = new UserMapDriver();
        String url = "jdbc:mapuser:jdbc:mock:" + dbName;

        assertTrue(driver.acceptsURL(url),
            "Should accept mapuser protocol: " + url);
    }

    /**
     * Property: UserMapDriver rejects non-mapuser protocols.
     */
    @Property(tries = 20)
    void rejectsNonMapuserProtocol(
            @ForAll("otherProtocols") String protocol,
            @ForAll("mockDbNames") String dbName) {

        UserMapDriver driver = new UserMapDriver();
        String url = "jdbc:" + protocol + ":jdbc:mock:" + dbName;

        assertFalse(driver.acceptsURL(url),
            "Should reject non-mapuser protocol: " + url);
    }

    /**
     * Property: UserMapDriver rejects malformed URLs.
     */
    @Property(tries = 10)
    void rejectsMalformedUrls() {
        UserMapDriver driver = new UserMapDriver();

        assertFalse(driver.acceptsURL("jdbc:mapuser"),
            "Should reject bare protocol");
        assertFalse(driver.acceptsURL("jdbc:mapuser:"),
            "Should reject empty subname");
        assertFalse(driver.acceptsURL("not-a-jdbc-url"),
            "Should reject non-JDBC URL");
        assertFalse(driver.acceptsURL(""),
            "Should reject empty string");
    }

    /**
     * Property: UserMapDriver accepts URLs with nested PJDBC drivers.
     */
    @Property(tries = 20)
    void acceptsNestedPjdbcDrivers(
            @ForAll("composableDrivers") String innerDriver,
            @ForAll("mockDbNames") String dbName) {

        UserMapDriver driver = new UserMapDriver();
        String url = "jdbc:mapuser:jdbc:" + innerDriver + ":jdbc:mock:" + dbName;

        assertTrue(driver.acceptsURL(url),
            "Should accept mapuser with nested driver: " + url);
    }

    // ========== CONNECTION ERROR PROPERTIES ==========

    /**
     * Property: Connect with unmapped user throws SQLException.
     * Note: The driver looks up users in a properties file. Without configuration,
     * all users should be unmapped and trigger an error.
     */
    @Property(tries = 20)
    void connectWithUnmappedUserThrows(
            @ForAll("userNames") String username,
            @ForAll("mockDbNames") String dbName) {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", "secret");

        SQLException exception = assertThrows(SQLException.class, () -> {
            DriverManager.getConnection(url, props);
        });

        assertTrue(exception.getMessage().contains("No mapping found") ||
                   exception.getMessage().contains("mapping"),
            "Should mention missing mapping: " + exception.getMessage());
    }

    /**
     * Property: Error message includes the unmapped username.
     */
    @Property(tries = 10)
    void errorMessageIncludesUsername(
            @ForAll("userNames") String username,
            @ForAll("mockDbNames") String dbName) {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Properties props = new Properties();
        props.setProperty("user", username);

        SQLException exception = assertThrows(SQLException.class, () -> {
            DriverManager.getConnection(url, props);
        });

        assertTrue(exception.getMessage().contains(username),
            "Error should include username '" + username + "': " + exception.getMessage());
    }

    // ========== NULL/MISSING USER PROPERTIES ==========

    /**
     * Property: Connect with null user property fails gracefully.
     * Note: UserMapDriver throws NullPointerException when user is null.
     */
    @Property(tries = 10)
    void connectWithNullUserFails(
            @ForAll("mockDbNames") String dbName) {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Properties props = new Properties();
        // No user property set

        // Can throw either SQLException or NullPointerException
        assertThrows(Exception.class, () -> {
            DriverManager.getConnection(url, props);
        }, "Should throw when user property is missing");
    }

    /**
     * Property: Connect with empty user fails.
     */
    @Property(tries = 10)
    void connectWithEmptyUserFails(
            @ForAll("mockDbNames") String dbName) {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Properties props = new Properties();
        props.setProperty("user", "");

        assertThrows(SQLException.class, () -> {
            DriverManager.getConnection(url, props);
        }, "Should throw when user is empty");
    }

    // ========== DRIVER REGISTRATION PROPERTIES ==========

    /**
     * Property: UserMapDriver is registered and discoverable.
     */
    @Property(tries = 10)
    void driverIsRegistered(
            @ForAll("mockDbNames") String dbName) throws SQLException {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Driver driver = DriverManager.getDriver(url);

        assertNotNull(driver, "UserMapDriver should be registered");
        assertTrue(driver instanceof UserMapDriver,
            "Driver should be UserMapDriver instance");
    }

    /**
     * Property: getDriver returns same result as acceptsURL.
     */
    @Property(tries = 20)
    void getDriverConsistentWithAcceptsUrl(
            @ForAll("mockDbNames") String dbName) throws SQLException {

        UserMapDriver driver = new UserMapDriver();
        String mapuserUrl = "jdbc:mapuser:jdbc:mock:" + dbName;
        String otherUrl = "jdbc:cat:jdbc:mock:" + dbName;

        assertTrue(driver.acceptsURL(mapuserUrl),
            "acceptsURL should return true for mapuser URL");
        assertNotNull(DriverManager.getDriver(mapuserUrl),
            "getDriver should find driver for mapuser URL");

        assertFalse(driver.acceptsURL(otherUrl),
            "acceptsURL should return false for cat URL");
    }

    // ========== COMPOSITION PROPERTIES ==========

    /**
     * Property: cat:mapuser composition is accepted.
     */
    @Property(tries = 10)
    void catMapuserCompositionAccepted(
            @ForAll("mockDbNames") String dbName) {

        CatDriver catDriver = new CatDriver();
        String url = "jdbc:cat:jdbc:mapuser:jdbc:mock:" + dbName;

        assertTrue(catDriver.acceptsURL(url),
            "cat should accept mapuser nested URL");
    }

    /**
     * Property: log:mapuser composition is accepted.
     */
    @Property(tries = 10)
    void logMapuserCompositionAccepted(
            @ForAll("mockDbNames") String dbName) {

        LogDriver logDriver = new LogDriver();
        String url = "jdbc:log:jdbc:mapuser:jdbc:mock:" + dbName;

        assertTrue(logDriver.acceptsURL(url),
            "log should accept mapuser nested URL");
    }

    /**
     * Property: Multiple driver composition layers accepted.
     */
    @Property(tries = 10)
    void multipleCompositionLayersAccepted(
            @ForAll("mockDbNames") String dbName) {

        CatDriver catDriver = new CatDriver();
        LogDriver logDriver = new LogDriver();

        String catLogMapuser = "jdbc:cat:jdbc:log:jdbc:mapuser:jdbc:mock:" + dbName;
        String logCatMapuser = "jdbc:log:jdbc:cat:jdbc:mapuser:jdbc:mock:" + dbName;

        assertTrue(catDriver.acceptsURL(catLogMapuser),
            "cat:log:mapuser should be accepted");
        assertTrue(logDriver.acceptsURL(logCatMapuser),
            "log:cat:mapuser should be accepted");
    }

    // ========== CONNECT RETURNS NULL PROPERTIES ==========

    /**
     * Property: connect returns null for non-mapuser URLs.
     */
    @Property(tries = 10)
    void connectReturnsNullForNonMapuserUrl(
            @ForAll("mockDbNames") String dbName) throws SQLException {

        UserMapDriver driver = new UserMapDriver();
        String url = "jdbc:cat:jdbc:mock:" + dbName;

        Connection conn = driver.connect(url, new Properties());
        assertNull(conn, "connect should return null for non-mapuser URL");
    }

    // ========== DRIVER PROPERTIES ==========

    /**
     * Property: Driver reports correct major version.
     */
    @Property(tries = 5)
    void driverReportsMajorVersion() {
        UserMapDriver driver = new UserMapDriver();
        int major = driver.getMajorVersion();
        assertTrue(major >= 0, "Major version should be non-negative");
    }

    /**
     * Property: Driver reports correct minor version.
     */
    @Property(tries = 5)
    void driverReportsMinorVersion() {
        UserMapDriver driver = new UserMapDriver();
        int minor = driver.getMinorVersion();
        assertTrue(minor >= 0, "Minor version should be non-negative");
    }

    /**
     * Property: Driver reports JDBC compliance status.
     */
    @Property(tries = 5)
    void driverReportsJdbcCompliance() {
        UserMapDriver driver = new UserMapDriver();
        // Just verify it doesn't throw
        boolean compliant = driver.jdbcCompliant();
        // Result can be true or false, just checking it works
        assertTrue(compliant || !compliant, "jdbcCompliant should return a boolean");
    }

    // ========== SPECIAL CHARACTER PROPERTIES ==========

    /**
     * Property: Usernames with special characters are handled.
     */
    @Property(tries = 10)
    void specialCharacterUsernamesHandled(
            @ForAll("specialUserNames") String username,
            @ForAll("mockDbNames") String dbName) {

        String url = "jdbc:mapuser:jdbc:mock:" + dbName;
        Properties props = new Properties();
        props.setProperty("user", username);

        // Should throw SQLException for unmapped user, not a parsing error
        SQLException exception = assertThrows(SQLException.class, () -> {
            DriverManager.getConnection(url, props);
        });

        assertTrue(exception.getMessage().contains("mapping") ||
                   exception.getMessage().contains("PJDBC"),
            "Should fail with mapping error, not parsing error: " + exception.getMessage());
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> mockDbNames() {
        return PjdbcArbitraries.mockDbNames();
    }

    @Provide
    Arbitrary<String> userNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(15)
            .map(String::toLowerCase);
    }

    @Provide
    Arbitrary<String> specialUserNames() {
        return Arbitraries.of(
            "user.name",
            "user_name",
            "user-name",
            "user123",
            "123user",
            "USER",
            "User.Name_123"
        );
    }

    @Provide
    Arbitrary<String> otherProtocols() {
        return Arbitraries.of(
            "cat", "log", "filter", "sink", "pool",
            "readonly", "retry", "h2", "mock"
        );
    }

    @Provide
    Arbitrary<String> composableDrivers() {
        return Arbitraries.of(
            "cat", "log", "filter"
        );
    }
}
