package org.pjdbc.debug;

import java.io.PrintStream;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.pjdbc.sql.PjdbcEventListener;

/**
 * Pre-built event listener that logs all PJDBC events for debugging.
 *
 * <p>Usage:
 * <pre>{@code
 * // Enable debug logging
 * PjdbcDebug.enable();
 *
 * // Or with custom output
 * PjdbcDebug.enable(System.err);
 *
 * // Or register directly
 * PjdbcListeners.register(new DebugEventListener());
 * }</pre>
 *
 * <p>Output format:
 * <pre>
 * [PJDBC 14:23:45.123] RETRY sql="SELECT..." attempt=2 delay=100ms cause="Connection reset"
 * [PJDBC 14:23:45.234] CIRCUIT_BREAKER name="default" CLOSED → OPEN
 * [PJDBC 14:23:45.345] SQL_TRANSFORM "SELECT *" → "SELECT id, name"
 * </pre>
 *
 * @since 2.3.0
 */
public class DebugEventListener implements PjdbcEventListener {

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final PrintStream out;
    private final boolean includeStackTrace;

    /**
     * Create a debug listener that writes to System.err.
     */
    public DebugEventListener() {
        this(System.err, false);
    }

    /**
     * Create a debug listener with custom output.
     *
     * @param out the output stream
     */
    public DebugEventListener(PrintStream out) {
        this(out, false);
    }

    /**
     * Create a debug listener with custom output and stack trace option.
     *
     * @param out the output stream
     * @param includeStackTrace whether to include stack traces for errors
     */
    public DebugEventListener(PrintStream out, boolean includeStackTrace) {
        this.out = out;
        this.includeStackTrace = includeStackTrace;
    }

    private void log(String event, String message) {
        String timestamp = TIME_FORMAT.format(Instant.now());
        out.printf("[PJDBC %s] %-20s %s%n", timestamp, event, message);
    }

    @Override
    public void onRetry(String sql, SQLException cause, int attempt, long delayMs) {
        String sqlPreview = truncate(sql, 50);
        String causeMsg = cause != null ? cause.getMessage() : "unknown";
        log("RETRY", String.format("sql=\"%s\" attempt=%d delay=%dms cause=\"%s\"",
            sqlPreview, attempt, delayMs, truncate(causeMsg, 60)));

        if (includeStackTrace && cause != null) {
            cause.printStackTrace(out);
        }
    }

    @Override
    public void onCircuitBreakerStateChange(String name, String oldState, String newState) {
        log("CIRCUIT_BREAKER", String.format("name=\"%s\" %s → %s", name, oldState, newState));
    }

    @Override
    public void onSqlTransformed(String original, String transformed) {
        if (!original.equals(transformed)) {
            log("SQL_TRANSFORM", String.format("\"%s\" → \"%s\"",
                truncate(original, 40), truncate(transformed, 40)));
        }
    }

    @Override
    public void onFederatedQuery(String sql, List<String> targetUrls) {
        String targets = String.join(", ", targetUrls);
        log("FEDERATED_QUERY", String.format("sql=\"%s\" targets=[%s]",
            truncate(sql, 40), truncate(targets, 60)));
    }

    @Override
    public void onCircuitBreakerRejection(String name, String state) {
        log("CIRCUIT_REJECTED", String.format("name=\"%s\" state=%s", name, state));
    }

    @Override
    public void onChaosInjected(String type, String sql, String details) {
        log("CHAOS_INJECTED", String.format("type=%s sql=\"%s\" details=\"%s\"",
            type, truncate(sql, 30), truncate(details, 40)));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        s = s.replace("\n", " ").replace("\r", "");
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
