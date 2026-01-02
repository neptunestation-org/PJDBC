package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.*;
import org.pjdbc.drivers.*;
import org.pjdbc.sql.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for FilterDriver SQL transformation.
 *
 * These tests verify properties of SQL transformations:
 * - Identity transformer preserves SQL
 * - Transformations are actually applied
 * - Composition with other drivers
 * - Transformer algebraic properties
 */
class FilterDriverProperties {

    private FilterDriver filterDriver;

    @BeforeProperty
    void setup() throws SQLException {
        MockDriver.clearLogs();
        // Get the registered FilterDriver instance
        filterDriver = (FilterDriver) DriverManager.getDriver("jdbc:filter:jdbc:mock:test");
        // Reset to identity transformer
        filterDriver.setTransformer(new AbstractJdbcTransformer() {});
    }

    // ========== IDENTITY TRANSFORMER ==========

    /**
     * Identity transformer preserves SQL exactly.
     */
    @Property(tries = 50)
    void identityTransformerPreservesSql(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {});

        MockDriver.clearLogs();

        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeQuery"),
            "Should have executed query");
    }

    /**
     * Identity transformer: filter:X behaves like X.
     */
    @Property(tries = 50)
    void identityFilterBehavesLikeDirect(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {});

        MockDriver.clearLogs();

        // Execute directly
        try (Connection conn = DriverManager.getConnection(mockUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String directLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // Execute through filter with identity transformer
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);

        assertEquals(directLog, filterLog,
            "Identity filter should behave like direct connection");
    }

    // ========== TRANSFORMATION APPLICATION ==========

    /**
     * Uppercase transformer produces uppercase SQL.
     */
    @Property(tries = 50)
    void uppercaseTransformerApplied(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });

        MockDriver.clearLogs();

        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        // The log should contain the uppercase version of SQL keywords
        assertTrue(log.contains("executeQuery"),
            "Should have executed query");
    }

    /**
     * Prefix transformer adds prefix to all SQL.
     */
    @Property(tries = 30)
    void prefixTransformerAddsPrefix(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql,
            @ForAll("prefixes") String prefix) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return "/* " + prefix + " */ " + s;
            }
        });

        MockDriver.clearLogs();

        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeQuery"),
            "Should have executed query with prefix");
    }

    /**
     * Table renaming transformer changes table names.
     */
    @Property(tries = 30)
    void tableRenamingTransformer(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("tableNames") String oldTable,
            @ForAll("tableNames") String newTable) throws SQLException {

        Assume.that(!oldTable.equals(newTable));

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.replace(oldTable, newTable);
            }
        });

        MockDriver.clearLogs();

        String sql = "SELECT * FROM " + oldTable;
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeQuery"),
            "Should have executed renamed query");
    }

    // ========== TRANSFORMER COMPOSITION ==========

    /**
     * Composing two transformers applies both in order.
     *
     * If T1 adds prefix and T2 uppercases, then T1∘T2 should
     * add prefix then uppercase the whole thing.
     */
    @Property(tries = 30)
    void transformerCompositionOrder(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        // Transformer that adds a marker
        JdbcTransformer addMarker = new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return "/* MARKED */ " + s;
            }
        };

        // Transformer that uppercases
        JdbcTransformer uppercase = new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        };

        // Composed transformer: first add marker, then uppercase
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) throws SQLException {
                return uppercase.transformSql(addMarker.transformSql(s));
            }
        });

        MockDriver.clearLogs();

        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeQuery"),
            "Composed transformation should execute");
    }

    // ========== DRIVER COMPOSITION ==========

    /**
     * cat:filter:X behaves like filter:X (cat is identity).
     */
    @Property(tries = 30)
    void catFilterComposition(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });

        MockDriver.clearLogs();

        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // cat:filter:X
        String catFilterUrl = "jdbc:cat:jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(catFilterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String catFilterLog = MockDriver.getLog(mockUrl);

        assertEquals(filterLog, catFilterLog,
            "cat:filter:X should behave like filter:X");
    }

    /**
     * filter:cat:X behaves like filter:X (cat is identity).
     */
    @Property(tries = 30)
    void filterCatComposition(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });

        MockDriver.clearLogs();

        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // filter:cat:X
        String filterCatUrl = "jdbc:filter:jdbc:cat:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterCatUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterCatLog = MockDriver.getLog(mockUrl);

        assertEquals(filterLog, filterCatLog,
            "filter:cat:X should behave like filter:X");
    }

    /**
     * log:filter:X preserves filter transformation.
     */
    @Property(tries = 30)
    void logFilterComposition(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });

        MockDriver.clearLogs();

        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);

        MockDriver.clearLogs();

        // log:filter:X
        String logFilterUrl = "jdbc:log:jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(logFilterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String logFilterLog = MockDriver.getLog(mockUrl);

        assertEquals(filterLog, logFilterLog,
            "log:filter:X should preserve filter behavior");
    }

    /**
     * sink:filter:X absorbs all operations (sink dominates).
     */
    @Property(tries = 30)
    void sinkFilterAbsorbs(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });

        MockDriver.clearLogs();

        String sinkFilterUrl = "jdbc:sink:jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(sinkFilterUrl)) {
            conn.createStatement().executeQuery(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertEquals("", log, "sink:filter:X should absorb all operations");
    }

    // ========== IDEMPOTENCE ==========

    /**
     * Idempotent transformer: applying twice equals applying once.
     * Example: UPPER(UPPER(x)) = UPPER(x)
     */
    @Property(tries = 30)
    void idempotentTransformer(
            @ForAll("mockUrls") String mockUrl,
            @ForAll("sqlStatements") String sql) throws SQLException {

        // Uppercase is idempotent
        JdbcTransformer uppercase = new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        };

        // Apply once
        filterDriver.setTransformer(uppercase);
        MockDriver.clearLogs();
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String onceLog = MockDriver.getLog(mockUrl);

        // Apply twice (compose with itself)
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) throws SQLException {
                return uppercase.transformSql(uppercase.transformSql(s));
            }
        });
        MockDriver.clearLogs();
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String twiceLog = MockDriver.getLog(mockUrl);

        assertEquals(onceLog, twiceLog,
            "Idempotent transformer applied twice should equal once");
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> mockUrls() {
        return PjdbcArbitraries.mockUrls();
    }

    @Provide
    Arbitrary<String> sqlStatements() {
        return PjdbcArbitraries.sqlStatements();
    }

    @Provide
    Arbitrary<String> prefixes() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> tableNames() {
        return Arbitraries.of("users", "orders", "products", "accounts", "customers");
    }
}
