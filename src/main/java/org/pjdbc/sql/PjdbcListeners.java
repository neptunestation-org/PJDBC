package org.pjdbc.sql;

import java.sql.SQLException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central registry for PJDBC event listeners.
 *
 * <p>Listeners can be registered programmatically or via ServiceLoader.
 * All registered listeners will receive callbacks from PJDBC drivers.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Programmatic registration
 * PjdbcListeners.register(new MyEventListener());
 *
 * // ServiceLoader (create META-INF/services/org.pjdbc.sql.PjdbcEventListener)
 * // containing fully qualified class names, one per line
 * }</pre>
 *
 * <p>Thread Safety: This class is thread-safe. Listeners are stored in a
 * CopyOnWriteArrayList to allow safe iteration during notification while
 * still permitting registration/unregistration.
 *
 * @since 2.2.0
 */
public final class PjdbcListeners {

    private static final Logger LOG = Logger.getLogger(PjdbcListeners.class.getName());

    private static final CopyOnWriteArrayList<PjdbcEventListener> listeners = new CopyOnWriteArrayList<>();

    private static volatile boolean serviceLoaderInitialized = false;

    private PjdbcListeners() {} // Utility class

    /**
     * Register a listener to receive PJDBC events.
     *
     * @param listener the listener to register
     */
    public static void register(PjdbcEventListener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
            LOG.fine(() -> "Registered PjdbcEventListener: " + listener.getClass().getName());
        }
    }

    /**
     * Unregister a previously registered listener.
     *
     * @param listener the listener to unregister
     * @return true if the listener was found and removed
     */
    public static boolean unregister(PjdbcEventListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            LOG.fine(() -> "Unregistered PjdbcEventListener: " + listener.getClass().getName());
        }
        return removed;
    }

    /**
     * Clear all registered listeners.
     * Useful for testing.
     */
    public static void clear() {
        listeners.clear();
        LOG.fine("Cleared all PjdbcEventListeners");
    }

    /**
     * Load listeners from ServiceLoader.
     * Called automatically on first notification, but can be called explicitly.
     */
    public static void loadFromServiceLoader() {
        if (!serviceLoaderInitialized) {
            synchronized (PjdbcListeners.class) {
                if (!serviceLoaderInitialized) {
                    try {
                        ServiceLoader<PjdbcEventListener> loader = ServiceLoader.load(PjdbcEventListener.class);
                        for (PjdbcEventListener listener : loader) {
                            register(listener);
                            LOG.fine(() -> "Loaded PjdbcEventListener from ServiceLoader: " +
                                listener.getClass().getName());
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Failed to load PjdbcEventListeners from ServiceLoader", e);
                    }
                    serviceLoaderInitialized = true;
                }
            }
        }
    }

    /**
     * Get the number of registered listeners.
     * @return the listener count
     */
    public static int listenerCount() {
        return listeners.size();
    }

    // ========== Notification methods ==========

    private static void ensureInitialized() {
        if (!serviceLoaderInitialized) {
            loadFromServiceLoader();
        }
    }

    /**
     * Notify listeners of a retry event.
     */
    public static void fireRetry(String sql, SQLException cause, int attempt, long delayMs) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onRetry(sql, cause, attempt, delayMs);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onRetry", e);
            }
        }
    }

    /**
     * Notify listeners of a circuit breaker state change.
     */
    public static void fireCircuitBreakerStateChange(String name, String oldState, String newState) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onCircuitBreakerStateChange(name, oldState, newState);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onCircuitBreakerStateChange", e);
            }
        }
    }

    /**
     * Notify listeners of SQL transformation.
     */
    public static void fireSqlTransformed(String original, String transformed) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onSqlTransformed(original, transformed);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onSqlTransformed", e);
            }
        }
    }

    /**
     * Notify listeners of a federated query.
     */
    public static void fireFederatedQuery(String sql, List<String> targetUrls) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onFederatedQuery(sql, targetUrls);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onFederatedQuery", e);
            }
        }
    }

    /**
     * Notify listeners of a circuit breaker rejection.
     */
    public static void fireCircuitBreakerRejection(String name, String state) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onCircuitBreakerRejection(name, state);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onCircuitBreakerRejection", e);
            }
        }
    }

    /**
     * Notify listeners of chaos injection.
     */
    public static void fireChaosInjected(String type, String sql, String details) {
        ensureInitialized();
        for (PjdbcEventListener listener : listeners) {
            try {
                listener.onChaosInjected(type, sql, details);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Exception in PjdbcEventListener.onChaosInjected", e);
            }
        }
    }
}
