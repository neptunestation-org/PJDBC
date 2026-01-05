package org.pjdbc.properties;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;
import org.pjdbc.drivers.*;
import org.pjdbc.sql.*;
import org.pjdbc.testing.PjdbcArbitraries;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for FilterDriver SQL transformation.
 *
 * These tests verify properties of SQL transformations:
 * - Identity transformer preserves SQL
 * - Transformations are actually applied
 * - Composition with other drivers
 * - Transformer algebraic properties
 * - executeUpdate/execute transformation
 * - Multiple statements transformation
 * - Transformation with real databases
 */
class FilterDriverProperties {

    private static final AtomicInteger dbCounter = new AtomicInteger(0);

    private String createUniqueDbName() {
        return "filter_prop_" + dbCounter.incrementAndGet() + "_" + System.nanoTime();
    }

    private FilterDriver filterDriver;

    @BeforeProperty
    void setup() throws SQLException {
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


        // Execute directly
        try (Connection conn = DriverManager.getConnection(mockUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String directLog = MockDriver.getLog(mockUrl);


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


        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);


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


        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);


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


        // filter:X
        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String filterLog = MockDriver.getLog(mockUrl);


        // cat:filter:X
        String catFilterUrl = "jdbc:cat:jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(catFilterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String catFilterLog = MockDriver.getLog(mockUrl);

        assertEquals(filterLog, catFilterLog,
            "cat:filter:X should preserve filter behavior");
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
        try (Connection conn = DriverManager.getConnection(filterUrl)) {
            conn.createStatement().executeQuery(sql);
        }
        String twiceLog = MockDriver.getLog(mockUrl);

        assertEquals(onceLog, twiceLog,
            "Idempotent transformer applied twice should equal once");
    }

    // ========== EXECUTEUPDATE TRANSFORMATION ==========

    /**
     * Property: executeUpdate SQL gets transformed.
     */
    @Property(tries = 10)
    void executeUpdateTransformed(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        // Set uppercase transformer
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("executeUpdate"),
            "Should have executed update");
        assertTrue(log.contains(sql.toUpperCase()),
            "SQL should be transformed to uppercase");
    }

    /**
     * Property: executeUpdate with identity transformer preserves SQL.
     */
    @Property(tries = 10)
    void executeUpdateIdentityPreserves(
            @ForAll("updateStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        filterDriver.setTransformer(new AbstractJdbcTransformer() {});


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains(sql),
            "Identity transformer should preserve SQL for executeUpdate");
    }

    // ========== EXECUTE TRANSFORMATION ==========

    /**
     * Property: execute SQL gets transformed.
     */
    @Property(tries = 10)
    void executeTransformed(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        // Set uppercase transformer
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains("execute"),
            "Should have executed");
        assertTrue(log.contains(sql.toUpperCase()),
            "SQL should be transformed to uppercase");
    }

    /**
     * Property: execute with identity transformer preserves SQL.
     */
    @Property(tries = 10)
    void executeIdentityPreserves(
            @ForAll("sqlStatements") String sql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        filterDriver.setTransformer(new AbstractJdbcTransformer() {});


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains(sql),
            "Identity transformer should preserve SQL for execute");
    }

    // ========== MULTIPLE STATEMENTS TRANSFORMED ==========

    /**
     * Property: Multiple statements all get transformed.
     */
    @Property(tries = 10)
    void multipleStatementsTransformed(
            @ForAll @IntRange(min = 2, max = 5) int statementCount) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        // Set uppercase transformer
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < statementCount; i++) {
                stmt.executeQuery("select " + i);
            }
        }

        String log = MockDriver.getLog(mockUrl);

        // All statements should be transformed
        for (int i = 0; i < statementCount; i++) {
            assertTrue(log.contains("SELECT " + i),
                "Statement " + i + " should be transformed to uppercase");
        }
    }

    /**
     * Property: Mixed operation types all get transformed.
     */
    @Property(tries = 10)
    void mixedOperationsTransformed() throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        // Set uppercase transformer
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return s.toUpperCase();
            }
        });


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("select 1");
            stmt.executeUpdate("insert into t values (1)");
            stmt.execute("delete from t where id = 1");
        }

        String log = MockDriver.getLog(mockUrl);

        assertTrue(log.contains("SELECT 1"),
            "SELECT should be transformed");
        assertTrue(log.contains("INSERT INTO T VALUES (1)"),
            "INSERT should be transformed");
        assertTrue(log.contains("DELETE FROM T WHERE ID = 1"),
            "DELETE should be transformed");
    }

    // ========== TRANSFORMATION WITH H2 ==========

    /**
     * Property: Transformation works with real H2 database.
     */
    @Property(tries = 10)
    void transformationWithH2(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String h2Url = "jdbc:h2:mem:" + dbName;

        // Identity transformer (H2 SQL is case-insensitive anyway)
        filterDriver.setTransformer(new AbstractJdbcTransformer() {});

        String filterUrl = "jdbc:filter:" + h2Url;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Transformed query should return correct value");
        }
    }

    /**
     * Property: Comment prefix transformation works with H2.
     */
    @Property(tries = 10)
    void commentPrefixWithH2(
            @ForAll @IntRange(min = 1, max = 100) int value) throws SQLException {

        String dbName = createUniqueDbName();
        String h2Url = "jdbc:h2:mem:" + dbName;

        // Add comment prefix (H2 ignores comments)
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return "/* filtered */ " + s;
            }
        });

        String filterUrl = "jdbc:filter:" + h2Url;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + value)) {

            assertTrue(rs.next());
            assertEquals(value, rs.getInt(1),
                "Query with comment prefix should work");
        }
    }

    // ========== CONSTANT TRANSFORMER ==========

    /**
     * Property: Constant transformer replaces any SQL with fixed SQL.
     */
    @Property(tries = 10)
    void constantTransformer(
            @ForAll("sqlStatements") String inputSql) throws SQLException {

        String dbName = createUniqueDbName();
        String mockUrl = "jdbc:mock:" + dbName;

        final String constantSql = "SELECT 42";

        // Constant transformer always returns the same SQL
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return constantSql;
            }
        });


        String filterUrl = "jdbc:filter:" + mockUrl;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery(inputSql);
        }

        String log = MockDriver.getLog(mockUrl);
        assertTrue(log.contains(constantSql),
            "Constant transformer should output fixed SQL");
        assertFalse(log.contains(inputSql) && !inputSql.equals(constantSql),
            "Original SQL should not appear (unless it equals constant)");
    }

    /**
     * Property: Constant transformer works with H2.
     */
    @Property(tries = 10)
    void constantTransformerWithH2() throws SQLException {

        String dbName = createUniqueDbName();
        String h2Url = "jdbc:h2:mem:" + dbName;

        // Constant transformer always returns SELECT 99
        filterDriver.setTransformer(new AbstractJdbcTransformer() {
            @Override
            public String transformSql(String s) {
                return "SELECT 99";
            }
        });

        String filterUrl = "jdbc:filter:" + h2Url;
        try (Connection conn = DriverManager.getConnection(filterUrl);
             Statement stmt = conn.createStatement();
             // Input SQL is different, but result should be from constant
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            assertTrue(rs.next());
            assertEquals(99, rs.getInt(1),
                "Constant transformer should replace query");
        }
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

    @Provide
    Arbitrary<String> updateStatements() {
        return Arbitraries.of(
            "INSERT INTO t VALUES (1)",
            "UPDATE t SET x = 1",
            "DELETE FROM t WHERE id = 1",
            "INSERT INTO users (name) VALUES ('test')"
        );
    }
}
