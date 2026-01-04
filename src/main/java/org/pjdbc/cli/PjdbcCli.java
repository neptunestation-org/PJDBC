package org.pjdbc.cli;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.pjdbc.annotations.DriverCapability;
import org.pjdbc.annotations.DriverParameter;
import org.pjdbc.capabilities.PjdbcCapabilities;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.validation.ParameterValidator;

/**
 * Command-line interface for PJDBC URL validation and driver discovery.
 *
 * <p>Commands:
 * <ul>
 *   <li>{@code validate <url>} - Parse and validate a PJDBC URL</li>
 *   <li>{@code list} - List all available drivers</li>
 *   <li>{@code show <prefix>} - Show details for a specific driver</li>
 *   <li>{@code test <url>} - Test a database connection</li>
 *   <li>{@code chain <url>} - Show the driver chain for a URL</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * java -cp pjdbc.jar org.pjdbc.cli.PjdbcCli validate "jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/db"
 * java -cp pjdbc.jar org.pjdbc.cli.PjdbcCli list
 * java -cp pjdbc.jar org.pjdbc.cli.PjdbcCli show retry
 * </pre>
 */
public class PjdbcCli {

    private static final String VERSION = "1.7.0";
    private static final String USAGE = """
        PJDBC CLI - URL Validation and Driver Discovery Tool

        Usage: pjdbc <command> [options]

        Commands:
          validate <url>    Parse and validate a PJDBC URL
          list              List all available drivers
          show <prefix>     Show details for a specific driver
          chain <url>       Show the driver chain for a URL
          test <url>        Test a database connection

        Options:
          --help, -h        Show this help message
          --version, -v     Show version information

        Examples:
          pjdbc validate "jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/db"
          pjdbc list
          pjdbc show retry
          pjdbc chain "jdbc:cache:jdbc:retry:jdbc:postgresql://localhost/db"
        """;

    private final PrintStream out;
    private final PrintStream err;

    public PjdbcCli() {
        this(System.out, System.err);
    }

    public PjdbcCli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static void main(String[] args) {
        PjdbcCli cli = new PjdbcCli();
        int exitCode = cli.run(args);
        System.exit(exitCode);
    }

    public int run(String[] args) {
        if (args.length == 0) {
            out.println(USAGE);
            return 0;
        }

        String command = args[0];

        return switch (command) {
            case "--help", "-h", "help" -> {
                out.println(USAGE);
                yield 0;
            }
            case "--version", "-v", "version" -> {
                out.println("PJDBC CLI version " + VERSION);
                yield 0;
            }
            case "validate" -> {
                if (args.length < 2) {
                    err.println("Error: validate requires a URL argument");
                    yield 1;
                }
                yield validate(args[1]);
            }
            case "list" -> list();
            case "show" -> {
                if (args.length < 2) {
                    err.println("Error: show requires a driver prefix argument");
                    yield 1;
                }
                yield show(args[1]);
            }
            case "chain" -> {
                if (args.length < 2) {
                    err.println("Error: chain requires a URL argument");
                    yield 1;
                }
                yield chain(args[1]);
            }
            case "test" -> {
                if (args.length < 2) {
                    err.println("Error: test requires a URL argument");
                    yield 1;
                }
                yield test(args[1]);
            }
            default -> {
                err.println("Unknown command: " + command);
                err.println("Run 'pjdbc --help' for usage information");
                yield 1;
            }
        };
    }

    /**
     * Validate a PJDBC URL and show parsing results.
     */
    private int validate(String url) {
        out.println("Validating URL: " + url);
        out.println();

        try {
            // Parse the URL
            JdbcUrlParser parser = JdbcUrlParser.parse(url);

            out.println("✓ URL structure is valid");
            out.println();
            out.println("  Protocol:    " + parser.getProtocol());
            out.println("  Subprotocol: " + parser.getSubprotocol());
            out.println("  Subname:     " + parser.getSubname());

            // Show parameters
            Map<String, String> params = parser.getParameters();
            if (!params.isEmpty()) {
                out.println();
                out.println("  Parameters:");
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    out.println("    " + entry.getKey() + " = " + entry.getValue());
                }
            }

            // Validate parameters against driver annotations
            String prefix = parser.getSubprotocol();
            Class<?> driverClass = findDriverClass(prefix);

            if (driverClass != null) {
                out.println();
                out.println("  Driver: " + driverClass.getSimpleName());

                try {
                    ParameterValidator.validate(driverClass, params);
                    out.println("  ✓ All parameters valid");
                } catch (SQLException e) {
                    out.println("  ✗ Parameter validation failed:");
                    out.println("    " + e.getMessage());
                    return 1;
                }
            }

            // Recursively validate nested URL
            String subname = parser.getSubname();
            if (subname != null && subname.startsWith("jdbc:")) {
                out.println();
                out.println("Nested URL:");
                return validate(subname);
            }

            return 0;

        } catch (IllegalArgumentException e) {
            err.println("✗ Invalid URL: " + e.getMessage());
            return 1;
        }
    }

    /**
     * List all available PJDBC drivers.
     */
    private int list() {
        out.println("Available PJDBC Drivers");
        out.println("=======================");
        out.println();

        List<Class<?>> drivers = findAllDriverClasses();

        if (drivers.isEmpty()) {
            out.println("No drivers found. Ensure PJDBC drivers are on the classpath.");
            return 1;
        }

        // Find max prefix length for alignment
        int maxPrefixLen = drivers.stream()
            .map(c -> c.getAnnotation(DriverCapability.class))
            .filter(a -> a != null)
            .mapToInt(a -> a.prefix().length())
            .max()
            .orElse(10);

        String format = "  %-" + (maxPrefixLen + 2) + "s %s";

        for (Class<?> driverClass : drivers) {
            DriverCapability cap = driverClass.getAnnotation(DriverCapability.class);
            if (cap != null) {
                String capabilities = String.join(", ", cap.capabilities());
                out.printf(format + "%n", cap.prefix(), cap.description());
                if (!capabilities.isEmpty()) {
                    out.printf(format + "%n", "", "[" + capabilities + "]");
                }
            }
        }

        out.println();
        out.println("Total: " + drivers.size() + " drivers");
        out.println();
        out.println("Use 'pjdbc show <prefix>' for driver details");

        return 0;
    }

    /**
     * Show details for a specific driver.
     */
    private int show(String prefix) {
        Class<?> driverClass = findDriverClass(prefix);

        if (driverClass == null) {
            err.println("Driver not found: " + prefix);
            err.println("Use 'pjdbc list' to see available drivers");
            return 1;
        }

        DriverCapability cap = driverClass.getAnnotation(DriverCapability.class);
        if (cap == null) {
            err.println("Driver missing @DriverCapability annotation: " + prefix);
            return 1;
        }

        out.println("Driver: " + cap.prefix());
        out.println("========" + "=".repeat(cap.prefix().length()));
        out.println();
        out.println("Description: " + cap.description());
        out.println("Class:       " + driverClass.getName());

        String[] capabilities = cap.capabilities();
        if (capabilities.length > 0) {
            out.println("Capabilities: " + String.join(", ", capabilities));
        }

        out.println("Composable:  " + cap.composable());
        out.println("Terminal:    " + cap.terminal());

        // Show parameters
        DriverParameter[] params = driverClass.getAnnotationsByType(DriverParameter.class);
        if (params.length > 0) {
            out.println();
            out.println("Parameters:");
            out.println("-----------");

            for (DriverParameter param : params) {
                out.println();
                out.println("  " + param.name());
                out.println("    Type:        " + param.type().name().toLowerCase());
                if (!param.description().isEmpty()) {
                    out.println("    Description: " + param.description());
                }
                if (!param.defaultValue().isEmpty()) {
                    out.println("    Default:     " + param.defaultValue());
                }
                if (param.required()) {
                    out.println("    Required:    yes");
                }
                if (param.min() != Long.MIN_VALUE) {
                    out.println("    Min:         " + param.min());
                }
                if (param.max() != Long.MAX_VALUE) {
                    out.println("    Max:         " + param.max());
                }
                if (param.enumValues().length > 0) {
                    out.println("    Allowed:     " + String.join(", ", param.enumValues()));
                }
            }
        }

        out.println();
        out.println("URL Format:");
        out.println("  jdbc:" + cap.prefix() + "[param=value,...]:jdbc:<target>:...");

        return 0;
    }

    /**
     * Show the driver chain for a URL.
     */
    private int chain(String url) {
        out.println("Driver Chain Analysis");
        out.println("=====================");
        out.println();
        out.println("URL: " + url);
        out.println();

        List<String> chain = new ArrayList<>();
        String current = url;
        int depth = 0;

        while (current != null && current.startsWith("jdbc:")) {
            try {
                JdbcUrlParser parser = JdbcUrlParser.parse(current);
                String prefix = parser.getSubprotocol();
                Map<String, String> params = parser.getParameters();

                Class<?> driverClass = findDriverClass(prefix);
                String driverName = driverClass != null ? driverClass.getSimpleName() : prefix;

                StringBuilder line = new StringBuilder();
                line.append("  ".repeat(depth));
                line.append(depth == 0 ? "→ " : "└─→ ");
                line.append(driverName);

                if (!params.isEmpty()) {
                    line.append(" [");
                    List<String> paramList = new ArrayList<>();
                    for (Map.Entry<String, String> e : params.entrySet()) {
                        paramList.add(e.getKey() + "=" + e.getValue());
                    }
                    line.append(String.join(", ", paramList));
                    line.append("]");
                }

                chain.add(line.toString());

                current = parser.getSubname();
                depth++;

            } catch (IllegalArgumentException e) {
                chain.add("  ".repeat(depth) + "└─→ " + current + " (terminal)");
                break;
            }
        }

        out.println("Chain (" + chain.size() + " layers):");
        for (String line : chain) {
            out.println(line);
        }

        return 0;
    }

    /**
     * Test a database connection.
     */
    private int test(String url) {
        out.println("Testing connection: " + url);
        out.println();

        // First validate the URL
        int validateResult = validate(url);
        if (validateResult != 0) {
            return validateResult;
        }

        out.println();
        out.println("Attempting connection...");

        try {
            long start = System.currentTimeMillis();
            try (Connection conn = DriverManager.getConnection(url)) {
                long elapsed = System.currentTimeMillis() - start;
                out.println("✓ Connection successful (" + elapsed + "ms)");
                out.println();
                out.println("  Database: " + conn.getMetaData().getDatabaseProductName());
                out.println("  Version:  " + conn.getMetaData().getDatabaseProductVersion());
                out.println("  Driver:   " + conn.getMetaData().getDriverName());
            }
            return 0;

        } catch (SQLException e) {
            err.println("✗ Connection failed: " + e.getMessage());
            if (e.getSQLState() != null) {
                err.println("  SQL State: " + e.getSQLState());
            }
            return 1;
        }
    }

    /**
     * Find a driver class by its prefix.
     */
    private Class<?> findDriverClass(String prefix) {
        for (Class<?> driverClass : findAllDriverClasses()) {
            DriverCapability cap = driverClass.getAnnotation(DriverCapability.class);
            if (cap != null && cap.prefix().equals(prefix)) {
                return driverClass;
            }
        }
        return null;
    }

    /**
     * Find all PJDBC driver classes with @DriverCapability.
     */
    private List<Class<?>> findAllDriverClasses() {
        List<Class<?>> drivers = new ArrayList<>();

        // Known driver classes - we scan the drivers package
        String[] driverNames = {
            "AuditDriver", "CachingDriver", "ChaosDriver", "CircuitBreakerDriver",
            "DataMaskingDriver", "FederatingDriver", "FilterDriver", "HazelcastCachingDriver",
            "HikariPoolDriver", "LoadBalancingDriver", "LogDriver", "MemcachedCachingDriver",
            "MetricsDriver", "NullDriver", "PoolDriver", "RateLimitDriver", "ReadonlyDriver",
            "RedisCachingDriver", "RetryDriver", "SchemaValidationDriver", "SinkDriver",
            "TeeDriver", "TimeoutDriver", "TracingDriver", "UserMapDriver", "VoidDriver"
        };

        for (String name : driverNames) {
            try {
                Class<?> clazz = Class.forName("org.pjdbc.drivers." + name);
                if (clazz.isAnnotationPresent(DriverCapability.class)) {
                    drivers.add(clazz);
                }
            } catch (ClassNotFoundException e) {
                // Skip if not found
            }
        }

        // Sort by prefix
        drivers.sort((a, b) -> {
            DriverCapability capA = a.getAnnotation(DriverCapability.class);
            DriverCapability capB = b.getAnnotation(DriverCapability.class);
            return capA.prefix().compareTo(capB.prefix());
        });

        return drivers;
    }
}
