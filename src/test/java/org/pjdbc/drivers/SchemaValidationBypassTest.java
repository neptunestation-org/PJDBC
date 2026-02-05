package org.pjdbc.drivers;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pjdbc.drivers.MockDriver;

@DisplayName("SchemaValidationDriver and SchemaTransformer Bypass Test")
class SchemaValidationBypassTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.pjdbc.drivers.SchemaValidationDriver");
        Class.forName("org.pjdbc.drivers.FilterDriver");
        Class.forName("org.pjdbc.drivers.MockDriver");
        Class.forName("org.h2.Driver");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    @DisplayName("blocks queries to non-whitelisted table names containing dots in quotes")
    void blocksTableWithDotInQuotes() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:bypass_dot;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            // "my.table" is a single identifier.
            // Previous flawed logic would see the dot and think it's schema-qualified,
            // extracting 'table"' as the table name.
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM \"my.table\"")
            );
            assertTrue(ex.getMessage().contains("my.table"),
                "Expected validation error for 'my.table' but got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("blocks queries to non-whitelisted tables with quotes in SchemaValidationDriver")
    void blocksNonWhitelistedTablesWithQuotes() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[allowedTables=allowed_table]:jdbc:h2:mem:bypass1;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT * FROM \"blocked_table\"")
            );
            assertTrue(ex.getMessage().contains("not in allowed tables list"),
                "Expected validation error but got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("blocks SELECT of blocked columns with quotes in SchemaValidationDriver")
    void blocksSelectBlockedColumnsWithQuotes() throws SQLException {
        conn = DriverManager.getConnection(
            "jdbc:schema[blockedColumns=ssn]:jdbc:h2:mem:bypass2;DB_CLOSE_DELAY=-1"
        );

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT, \"ssn\" VARCHAR(100))");

            SQLException ex = assertThrows(SQLException.class, () ->
                stmt.executeQuery("SELECT \"ssn\" FROM users")
            );
            assertTrue(ex.getMessage().contains("blocked"),
                "Expected validation error but got: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("prefixes quoted table names in SchemaTransformer")
    void prefixesQuotedTableNames() throws SQLException {
        // Use MockDriver to capture the transformed SQL
        conn = DriverManager.getConnection(
            "jdbc:filter[schema=tenant_123]:jdbc:mock:schema_transformer_bypass"
        );

        try (Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM \"users\"");
            String log = MockDriver.getLog("jdbc:mock:schema_transformer_bypass");
            // If bypass exists, it will be "executeQuery[SELECT * FROM \"users\"]"
            // If fixed, it should be "executeQuery[SELECT * FROM tenant_123.\"users\"]"
            assertTrue(log.contains("tenant_123.\"users\""),
                "Expected schema prefix but got: " + log);
        }
    }
}
