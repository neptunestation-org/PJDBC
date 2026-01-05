package org.pjdbc.debug;

import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.pjdbc.sql.PjdbcListeners;

/**
 * Utility class for enabling PJDBC debug output.
 *
 * <p>Provides a simple API for debugging PJDBC driver behavior:
 * <pre>{@code
 * // Enable all debug output
 * PjdbcDebug.enable();
 *
 * // Enable with custom stream
 * PjdbcDebug.enable(System.out);
 *
 * // Enable only event listener (no JUL logging)
 * PjdbcDebug.enableEvents();
 *
 * // Enable only JUL logging (no events)
 * PjdbcDebug.enableLogging();
 *
 * // Disable all
 * PjdbcDebug.disable();
 * }</pre>
 *
 * <p>Can also be enabled via system property:
 * <pre>
 * java -Dpjdbc.debug=true ...
 * </pre>
 *
 * @since 2.3.0
 */
public final class PjdbcDebug {

    private static final Logger PJDBC_LOGGER = Logger.getLogger("org.pjdbc");
    private static final String DEBUG_PROPERTY = "pjdbc.debug";

    private static volatile DebugEventListener activeListener;
    private static volatile Level previousLogLevel;
    private static volatile boolean loggingEnabled;

    static {
        // Auto-enable if system property is set
        if ("true".equalsIgnoreCase(System.getProperty(DEBUG_PROPERTY))) {
            enable();
        }
    }

    private PjdbcDebug() {} // Utility class

    /**
     * Enable all debug output (events + JUL logging) to System.err.
     */
    public static void enable() {
        enable(System.err);
    }

    /**
     * Enable all debug output (events + JUL logging) to custom stream.
     *
     * @param out the output stream
     */
    public static void enable(PrintStream out) {
        enableEvents(out);
        enableLogging();
    }

    /**
     * Enable event listener debugging only.
     */
    public static void enableEvents() {
        enableEvents(System.err);
    }

    /**
     * Enable event listener debugging with custom stream.
     *
     * @param out the output stream
     */
    public static void enableEvents(PrintStream out) {
        disableEvents(); // Remove any existing listener
        activeListener = new DebugEventListener(out);
        PjdbcListeners.register(activeListener);
    }

    /**
     * Enable java.util.logging for org.pjdbc at FINE level.
     */
    public static void enableLogging() {
        if (!loggingEnabled) {
            previousLogLevel = PJDBC_LOGGER.getLevel();
            PJDBC_LOGGER.setLevel(Level.FINE);

            // Ensure a console handler exists and accepts FINE
            boolean hasConsoleHandler = false;
            for (var handler : PJDBC_LOGGER.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setLevel(Level.FINE);
                    hasConsoleHandler = true;
                }
            }

            if (!hasConsoleHandler) {
                ConsoleHandler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                PJDBC_LOGGER.addHandler(handler);
            }

            loggingEnabled = true;
        }
    }

    /**
     * Disable all debug output.
     */
    public static void disable() {
        disableEvents();
        disableLogging();
    }

    /**
     * Disable event listener debugging.
     */
    public static void disableEvents() {
        if (activeListener != null) {
            PjdbcListeners.unregister(activeListener);
            activeListener = null;
        }
    }

    /**
     * Disable java.util.logging.
     */
    public static void disableLogging() {
        if (loggingEnabled) {
            if (previousLogLevel != null) {
                PJDBC_LOGGER.setLevel(previousLogLevel);
            }
            loggingEnabled = false;
        }
    }

    /**
     * Check if debug mode is currently enabled.
     *
     * @return true if any debug output is enabled
     */
    public static boolean isEnabled() {
        return activeListener != null || loggingEnabled;
    }

    /**
     * Get a summary of PJDBC configuration for debugging.
     *
     * @return multi-line debug info string
     */
    public static String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("PJDBC Debug Info\n");
        sb.append("================\n");
        sb.append("Debug enabled: ").append(isEnabled()).append("\n");
        sb.append("Event listener: ").append(activeListener != null ? "active" : "inactive").append("\n");
        sb.append("JUL logging: ").append(loggingEnabled ? "FINE" : "default").append("\n");
        sb.append("Registered listeners: ").append(PjdbcListeners.listenerCount()).append("\n");
        sb.append("Java version: ").append(System.getProperty("java.version")).append("\n");
        return sb.toString();
    }
}
