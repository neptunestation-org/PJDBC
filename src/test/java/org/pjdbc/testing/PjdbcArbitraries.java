package org.pjdbc.testing;

import net.jqwik.api.*;
import net.jqwik.api.arbitraries.*;

import java.util.*;
import java.util.stream.*;

/**
 * Shared arbitraries for PJDBC property-based testing.
 * Provides generators for JDBC URLs, driver chains, and SQL statements.
 */
public class PjdbcArbitraries {

    // ============ Driver Types ============

    /**
     * Passthrough drivers that don't modify data flow.
     * These satisfy identity-like properties when composed.
     */
    public static final List<String> PASSTHROUGH_DRIVERS = List.of(
        "cat"
    );

    /**
     * Drivers that can be safely chained for composition testing.
     * Excludes drivers requiring external resources.
     */
    public static final List<String> COMPOSABLE_DRIVERS = List.of(
        "cat", "filter", "sink"
    );

    /**
     * All available driver subprotocols.
     */
    public static final List<String> ALL_DRIVERS = List.of(
        "cat", "filter", "sink", "tee",
        "readonly", "retry", "chaos", "mask",
        "timeout", "circuitbreaker", "schema", "federate"
    );

    // ============ Basic Arbitraries ============

    /**
     * Generates valid mock database names.
     */
    public static Arbitrary<String> mockDbNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(10)
            .map(String::toLowerCase);
    }

    /**
     * Generates terminal mock URLs like "jdbc:mock:testdb".
     */
    public static Arbitrary<String> mockUrls() {
        return mockDbNames().map(name -> "jdbc:mock:" + name);
    }

    /**
     * Generates passthrough driver subprotocols.
     */
    public static Arbitrary<String> passthroughDrivers() {
        return Arbitraries.of(PASSTHROUGH_DRIVERS);
    }

    /**
     * Generates composable driver subprotocols.
     */
    public static Arbitrary<String> composableDrivers() {
        return Arbitraries.of(COMPOSABLE_DRIVERS);
    }

    // ============ Driver Chain Arbitraries ============

    /**
     * Generates driver chains of specified depth using given drivers.
     * @param drivers Arbitrary for driver subprotocols
     * @param terminalUrl The terminal (innermost) URL
     * @param minDepth Minimum chain depth
     * @param maxDepth Maximum chain depth
     */
    public static Arbitrary<String> driverChain(
            Arbitrary<String> drivers,
            String terminalUrl,
            int minDepth,
            int maxDepth) {

        return Arbitraries.integers()
            .between(minDepth, maxDepth)
            .flatMap(depth -> {
                if (depth == 0) {
                    return Arbitraries.just(terminalUrl);
                }
                return drivers.list().ofSize(depth)
                    .map(driverList -> {
                        StringBuilder url = new StringBuilder();
                        for (String driver : driverList) {
                            url.append("jdbc:").append(driver).append(":");
                        }
                        url.append(terminalUrl);
                        return url.toString();
                    });
            });
    }

    /**
     * Generates cat-only chains for identity law testing.
     */
    public static Arbitrary<String> catChains(int minDepth, int maxDepth) {
        return mockUrls().flatMap(terminal ->
            driverChain(Arbitraries.just("cat"), terminal, minDepth, maxDepth)
        );
    }

    /**
     * Generates passthrough driver chains.
     */
    public static Arbitrary<String> passthroughChains(int minDepth, int maxDepth) {
        return mockUrls().flatMap(terminal ->
            driverChain(passthroughDrivers(), terminal, minDepth, maxDepth)
        );
    }

    /**
     * Generates arbitrary composable driver chains.
     */
    public static Arbitrary<String> composableChains(int minDepth, int maxDepth) {
        return mockUrls().flatMap(terminal ->
            driverChain(composableDrivers(), terminal, minDepth, maxDepth)
        );
    }

    // ============ SQL Arbitraries ============

    /**
     * Generates simple SELECT statements.
     */
    public static Arbitrary<String> selectStatements() {
        Arbitrary<String> tables = Arbitraries.of(
            "users", "orders", "products", "accounts", "customers"
        );
        Arbitrary<String> columns = Arbitraries.of(
            "*", "id", "name", "id, name", "name, email"
        );

        return Combinators.combine(columns, tables)
            .as((cols, table) -> "SELECT " + cols + " FROM " + table);
    }

    /**
     * Generates simple INSERT statements.
     */
    public static Arbitrary<String> insertStatements() {
        return Arbitraries.of(
            "INSERT INTO users (name) VALUES ('test')",
            "INSERT INTO orders (user_id, amount) VALUES (1, 100)",
            "INSERT INTO products (name, price) VALUES ('item', 9.99)"
        );
    }

    /**
     * Generates various SQL statements for testing.
     */
    public static Arbitrary<String> sqlStatements() {
        return Arbitraries.oneOf(
            selectStatements(),
            insertStatements(),
            Arbitraries.of(
                "UPDATE users SET name = 'updated' WHERE id = 1",
                "DELETE FROM users WHERE id = 1"
            )
        );
    }

    // ============ Connection Properties ============

    /**
     * Generates connection properties.
     */
    public static Arbitrary<Properties> connectionProperties() {
        return Arbitraries.maps(
            Arbitraries.of("user", "password", "timeout", "autoCommit"),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
        ).ofMinSize(0).ofMaxSize(3)
        .map(map -> {
            Properties props = new Properties();
            props.putAll(map);
            return props;
        });
    }

    // ============ URL Parameter Arbitraries ============

    private static final String ALPHA_NUMERIC =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * Generates parameter maps for JDBC URLs.
     */
    public static Arbitrary<Map<String, String>> paramMaps() {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(8),
            Arbitraries.strings().withChars(ALPHA_NUMERIC.toCharArray()).ofMinLength(1).ofMaxLength(10)
        ).ofMinSize(0).ofMaxSize(3);
    }

    /**
     * Generates valid JDBC URLs with optional parameters.
     */
    public static Arbitrary<String> validJdbcUrls() {
        return Combinators.combine(
            Arbitraries.of(COMPOSABLE_DRIVERS),
            paramMaps(),
            mockUrls()
        ).as((driver, params, terminal) -> {
            StringBuilder url = new StringBuilder("jdbc:");
            url.append(driver);
            if (!params.isEmpty()) {
                url.append("[");
                url.append(params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")));
                url.append("]");
            }
            url.append(":").append(terminal);
            return url.toString();
        });
    }
}
