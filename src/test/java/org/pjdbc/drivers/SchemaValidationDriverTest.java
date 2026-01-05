package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pjdbc.drivers.SchemaValidationDriver.SchemaConfig;
import org.pjdbc.drivers.SchemaValidationDriver.ValidationMode;

@DisplayName("SchemaValidationDriver")
class SchemaValidationDriverTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure drivers are loaded
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.h2.Driver");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Nested
    @DisplayName("Configuration Parsing")
    class ConfigurationParsing {

        @Test
        @DisplayName("parses default configuration")
        void parsesDefaultConfiguration() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:schema_default;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertNotNull(config);
            assertEquals(ValidationMode.WHITELIST, config.getMode());
            assertFalse(config.isCaseSensitive());
            assertTrue(config.getAllowedTables().isEmpty());
            assertTrue(config.getBlockedTables().isEmpty());
        }

        @Test
        @DisplayName("parses allowed tables")
        void parsesAllowedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users;orders;products]:jdbc:h2:mem:schema_allowed;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertEquals(3, config.getAllowedTables().size());
            assertTrue(config.getAllowedTables().contains("users"));
            assertTrue(config.getAllowedTables().contains("orders"));
            assertTrue(config.getAllowedTables().contains("products"));
        }

        @Test
        @DisplayName("parses blocked tables")
        void parsesBlockedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedTables=audit;secrets,mode=blacklist]:jdbc:h2:mem:schema_blocked;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertEquals(ValidationMode.BLACKLIST, config.getMode());
            assertEquals(2, config.getBlockedTables().size());
            assertTrue(config.getBlockedTables().contains("audit"));
            assertTrue(config.getBlockedTables().contains("secrets"));
        }

        @Test
        @DisplayName("parses blocked columns")
        void parsesBlockedColumns() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=ssn;credit_card;password]:jdbc:h2:mem:schema_cols;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertEquals(3, config.getBlockedColumns().size());
            assertTrue(config.getBlockedColumns().contains("ssn"));
            assertTrue(config.getBlockedColumns().contains("credit_card"));
            assertTrue(config.getBlockedColumns().contains("password"));
        }

        @Test
        @DisplayName("parses case sensitive mode")
        void parsesCaseSensitiveMode() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=Users;ORDERS,caseSensitive=true]:jdbc:h2:mem:schema_case;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertTrue(config.isCaseSensitive());
            assertTrue(config.getAllowedTables().contains("Users"));
            assertTrue(config.getAllowedTables().contains("ORDERS"));
            assertFalse(config.getAllowedTables().contains("users"));
        }

        @Test
        @DisplayName("parses custom message")
        void parsesCustomMessage() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[message=Access Denied]:jdbc:h2:mem:schema_msg;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertEquals("Access Denied", config.getMessage());
        }

        @Test
        @DisplayName("parses metadata mode")
        void parsesMetadataMode() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[mode=metadata]:jdbc:h2:mem:schema_meta;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            assertEquals(ValidationMode.METADATA, config.getMode());
        }
    }

    @Nested
    @DisplayName("Whitelist Mode")
    class WhitelistMode {

        @Test
        @DisplayName("allows queries to whitelisted tables")
        void allowsWhitelistedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=test_table]:jdbc:h2:mem:whitelist1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT, name VARCHAR(100))");
                stmt.execute("INSERT INTO test_table VALUES (1, 'test')");

                ResultSet rs = stmt.executeQuery("SELECT * FROM test_table");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("id"));
            }
        }

        @Test
        @DisplayName("blocks queries to non-whitelisted tables")
        void blocksNonWhitelistedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:whitelist2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM blocked_table")
                );
                assertTrue(ex.getMessage().contains("not in allowed tables list"));
            }
        }

        @Test
        @DisplayName("blocks INSERT to non-whitelisted tables")
        void blocksInsertToNonWhitelisted() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:whitelist3;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("INSERT INTO secrets VALUES (1, 'secret')")
                );
                assertTrue(ex.getMessage().contains("secrets"));
                assertTrue(ex.getMessage().contains("not in allowed tables list"));
            }
        }

        @Test
        @DisplayName("blocks UPDATE to non-whitelisted tables")
        void blocksUpdateToNonWhitelisted() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:whitelist4;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("UPDATE audit SET value = 'x'")
                );
                assertTrue(ex.getMessage().contains("audit"));
            }
        }

        @Test
        @DisplayName("allows empty whitelist (all tables allowed)")
        void allowsEmptyWhitelist() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:whitelist5;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE any_table (id INT)");
                stmt.executeUpdate("INSERT INTO any_table VALUES (1)");
                ResultSet rs = stmt.executeQuery("SELECT * FROM any_table");
                assertTrue(rs.next());
            }
        }
    }

    @Nested
    @DisplayName("Blacklist Mode")
    class BlacklistMode {

        @Test
        @DisplayName("allows queries to non-blacklisted tables")
        void allowsNonBlacklistedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedTables=audit;secrets,mode=blacklist]:jdbc:h2:mem:blacklist1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, name VARCHAR(100))");
                stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice')");
                ResultSet rs = stmt.executeQuery("SELECT * FROM users");
                assertTrue(rs.next());
            }
        }

        @Test
        @DisplayName("blocks queries to blacklisted tables")
        void blocksBlacklistedTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedTables=audit;secrets,mode=blacklist]:jdbc:h2:mem:blacklist2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM audit")
                );
                assertTrue(ex.getMessage().contains("blocked"));
                assertTrue(ex.getMessage().contains("audit"));
            }
        }

        @Test
        @DisplayName("blocks INSERT to blacklisted tables")
        void blocksInsertToBlacklisted() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedTables=secrets,mode=blacklist]:jdbc:h2:mem:blacklist3;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("INSERT INTO secrets (key, value) VALUES ('a', 'b')")
                );
                assertTrue(ex.getMessage().contains("secrets"));
            }
        }
    }

    @Nested
    @DisplayName("Column Blocking")
    class ColumnBlocking {

        @Test
        @DisplayName("blocks SELECT of blocked columns")
        void blocksSelectBlockedColumns() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=ssn;password]:jdbc:h2:mem:col1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT id, name, ssn FROM users")
                );
                assertTrue(ex.getMessage().contains("ssn"));
                assertTrue(ex.getMessage().contains("blocked"));
            }
        }

        @Test
        @DisplayName("allows SELECT of non-blocked columns")
        void allowsNonBlockedColumns() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=ssn;password]:jdbc:h2:mem:col2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, name VARCHAR(100))");
                stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice')");
                ResultSet rs = stmt.executeQuery("SELECT id, name FROM users");
                assertTrue(rs.next());
            }
        }

        @Test
        @DisplayName("blocks INSERT into blocked columns")
        void blocksInsertBlockedColumns() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=secret_key]:jdbc:h2:mem:col3;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("INSERT INTO config (name, secret_key) VALUES ('a', 'b')")
                );
                assertTrue(ex.getMessage().contains("secret_key"));
            }
        }

        @Test
        @DisplayName("blocks UPDATE of blocked columns")
        void blocksUpdateBlockedColumns() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=password]:jdbc:h2:mem:col4;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("UPDATE users SET password = 'newpass'")
                );
                assertTrue(ex.getMessage().contains("password"));
            }
        }

        @Test
        @DisplayName("allows SELECT * (wildcard)")
        void allowsSelectWildcard() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[blockedColumns=hidden]:jdbc:h2:mem:col5;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
                // SELECT * doesn't extract individual columns
                ResultSet rs = stmt.executeQuery("SELECT * FROM test");
                assertNotNull(rs);
            }
        }
    }

    @Nested
    @DisplayName("Case Sensitivity")
    class CaseSensitivity {

        @Test
        @DisplayName("case insensitive matching by default")
        void caseInsensitiveByDefault() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:case1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE USERS (id INT)");
                // Should match despite different case
                stmt.executeUpdate("INSERT INTO USERS VALUES (1)");
                ResultSet rs = stmt.executeQuery("SELECT * FROM Users");
                assertTrue(rs.next());
            }
        }

        @Test
        @DisplayName("case sensitive matching when enabled")
        void caseSensitiveWhenEnabled() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users,caseSensitive=true]:jdbc:h2:mem:case2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM USERS")
                );
                assertTrue(ex.getMessage().contains("USERS"));
            }
        }
    }

    @Nested
    @DisplayName("Metadata Mode")
    class MetadataMode {

        @Test
        @DisplayName("programmatic table configuration")
        void programmaticTableConfiguration() throws SQLException {
            // Empty whitelist mode allows all tables
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:meta1;DB_CLOSE_DELAY=-1"
            );

            // Create tables - allowed since whitelist is empty
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE existing_table (id INT)");
                stmt.execute("CREATE TABLE another_table (name VARCHAR(100))");
            }

            // Add tables programmatically for testing
            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            config.addAllowedTable("existing_table");
            config.addAllowedTable("another_table");

            assertTrue(config.getAllowedTables().contains("existing_table"));
            assertTrue(config.getAllowedTables().contains("another_table"));
        }

        @Test
        @DisplayName("allows queries to existing tables")
        void allowsExistingTables() throws SQLException {
            // Empty whitelist allows all tables
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:meta2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE products (id INT, name VARCHAR(100))");
                stmt.executeUpdate("INSERT INTO products VALUES (1, 'Widget')");
                ResultSet rs = stmt.executeQuery("SELECT * FROM products");
                assertTrue(rs.next());
            }
        }

        @Test
        @DisplayName("metadata mode loads tables from database")
        void metadataModeLoadsFromDatabase() throws SQLException {
            // First create and populate the database
            Connection setup = DriverManager.getConnection("jdbc:h2:mem:meta3;DB_CLOSE_DELAY=-1");
            try (Statement stmt = setup.createStatement()) {
                stmt.execute("CREATE TABLE valid_table (id INT)");
            }
            // Keep setup open to maintain the database

            // Connect with metadata mode - loads existing tables at connect time
            conn = DriverManager.getConnection(
                "jdbc:schema[mode=metadata]:jdbc:h2:mem:meta3;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            // Tables loaded from metadata at connection time should be allowed
            // Note: H2 stores as uppercase, caseSensitive=false lowercases it
            assertTrue(config.getAllowedTables().contains("valid_table"),
                "Should contain valid_table from metadata, got: " + config.getAllowedTables());

            // Queries to existing tables should work
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT INTO valid_table VALUES (1)");
                ResultSet rs = stmt.executeQuery("SELECT * FROM valid_table");
                assertTrue(rs.next());
            }

            setup.close();
        }

        @Test
        @DisplayName("metadata mode blocks unknown tables")
        void metadataModeBlocksUnknownTables() throws SQLException {
            // First create and populate the database
            Connection setup = DriverManager.getConnection("jdbc:h2:mem:meta4;DB_CLOSE_DELAY=-1");
            try (Statement stmt = setup.createStatement()) {
                stmt.execute("CREATE TABLE known_table (id INT)");
            }
            // Keep setup open

            // Connect with metadata mode
            conn = DriverManager.getConnection(
                "jdbc:schema[mode=metadata]:jdbc:h2:mem:meta4;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                // Accessing a table not in metadata should fail
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM unknown_table")
                );
                assertTrue(ex.getMessage().contains("unknown_table"));
            }

            setup.close();
        }
    }

    @Nested
    @DisplayName("PreparedStatement Support")
    class PreparedStatementSupport {

        @Test
        @DisplayName("validates PreparedStatement SQL")
        void validatesPreparedStatement() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:prep1;DB_CLOSE_DELAY=-1"
            );

            SQLException ex = assertThrows(SQLException.class, () ->
                conn.prepareStatement("SELECT * FROM blocked_table WHERE id = ?")
            );
            assertTrue(ex.getMessage().contains("blocked_table"));
        }

        @Test
        @DisplayName("allows PreparedStatement for whitelisted tables")
        void allowsPreparedForWhitelisted() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:prep2;DB_CLOSE_DELAY=-1"
            );

            try (Statement setup = conn.createStatement()) {
                setup.execute("CREATE TABLE users (id INT, name VARCHAR(100))");
                setup.executeUpdate("INSERT INTO users VALUES (1, 'Alice')");
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                pstmt.setInt(1, 1);
                ResultSet rs = pstmt.executeQuery();
                assertTrue(rs.next());
                assertEquals("Alice", rs.getString("name"));
            }
        }
    }

    @Nested
    @DisplayName("JOIN Queries")
    class JoinQueries {

        @Test
        @DisplayName("validates all tables in JOIN")
        void validatesAllJoinTables() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users;orders]:jdbc:h2:mem:join1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM users JOIN secrets ON users.id = secrets.user_id")
                );
                assertTrue(ex.getMessage().contains("secrets"));
            }
        }

        @Test
        @DisplayName("allows JOIN with whitelisted tables")
        void allowsJoinWhitelisted() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users;orders]:jdbc:h2:mem:join2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE users (id INT, name VARCHAR(100))");
                stmt.execute("CREATE TABLE orders (id INT, user_id INT, total DECIMAL)");
                stmt.executeUpdate("INSERT INTO users VALUES (1, 'Alice')");
                stmt.executeUpdate("INSERT INTO orders VALUES (1, 1, 99.99)");

                ResultSet rs = stmt.executeQuery(
                    "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id"
                );
                assertTrue(rs.next());
            }
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperations {

        @Test
        @DisplayName("validates addBatch SQL")
        void validatesAddBatch() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:h2:mem:batch1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.addBatch("INSERT INTO users VALUES (1, 'a')");
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.addBatch("INSERT INTO blocked VALUES (2, 'b')")
                );
                assertTrue(ex.getMessage().contains("blocked"));
            }
        }
    }

    @Nested
    @DisplayName("Programmatic Configuration")
    class ProgrammaticConfiguration {

        @Test
        @DisplayName("can add allowed tables programmatically")
        void addsAllowedTablesProgrammatically() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:prog1;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            config.addAllowedTable("dynamic_table");

            assertTrue(config.getAllowedTables().contains("dynamic_table"));
        }

        @Test
        @DisplayName("can add blocked tables programmatically")
        void addsBlockedTablesProgrammatically() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[mode=blacklist]:jdbc:h2:mem:prog2;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            config.addBlockedTable("temp_table");

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM temp_table")
                );
                assertTrue(ex.getMessage().contains("temp_table"));
            }
        }

        @Test
        @DisplayName("can add blocked columns programmatically")
        void addsBlockedColumnsProgrammatically() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema:jdbc:h2:mem:prog3;DB_CLOSE_DELAY=-1"
            );

            SchemaConfig config = SchemaValidationDriver.getSchemaConfig(conn);
            config.addBlockedColumn("sensitive_field");

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT id, sensitive_field FROM data")
                );
                assertTrue(ex.getMessage().contains("sensitive_field"));
            }
        }
    }

    @Nested
    @DisplayName("Driver Chaining")
    class DriverChaining {

        @Test
        @DisplayName("works with cat driver")
        void worksWithCatDriver() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=test]:jdbc:cat:jdbc:h2:mem:chain1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INT)");
                stmt.executeUpdate("INSERT INTO test VALUES (1)");
                ResultSet rs = stmt.executeQuery("SELECT * FROM test");
                assertTrue(rs.next());
            }
        }

        @Test
        @DisplayName("works with readonly driver")
        void worksWithReadonlyDriver() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=users]:jdbc:readonly:jdbc:h2:mem:chain2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                // Both schema and readonly should block this
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("INSERT INTO blocked VALUES (1)")
                );
                // Schema validation should catch it first
                assertTrue(ex.getMessage().contains("blocked") || ex.getMessage().contains("not in allowed"));
            }
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("includes table name in error")
        void includesTableNameInError() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=allowed]:jdbc:h2:mem:err1;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM forbidden_table")
                );
                assertTrue(ex.getMessage().contains("forbidden_table"));
            }
        }

        @Test
        @DisplayName("uses custom error message prefix")
        void usesCustomMessagePrefix() throws SQLException {
            conn = DriverManager.getConnection(
                "jdbc:schema[allowedTables=ok,message=SECURITY VIOLATION]:jdbc:h2:mem:err2;DB_CLOSE_DELAY=-1"
            );

            try (Statement stmt = conn.createStatement()) {
                SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.executeQuery("SELECT * FROM blocked")
                );
                assertTrue(ex.getMessage().startsWith("SECURITY VIOLATION"));
            }
        }
    }
}
