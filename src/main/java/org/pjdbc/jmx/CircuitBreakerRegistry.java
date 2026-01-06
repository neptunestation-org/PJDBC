package org.pjdbc.jmx;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.pjdbc.drivers.CircuitBreakerDriver.CircuitBreaker;

/**
 * Registry for CircuitBreaker instances with optional JMX registration.
 *
 * <p>JMX registration is disabled by default. Enable via:
 * <ul>
 *   <li>System property: {@code -Dpjdbc.jmx.enabled=true}</li>
 *   <li>Programmatic: {@code CircuitBreakerRegistry.enableJmx()}</li>
 * </ul>
 *
 * <p>When enabled, circuit breakers are registered under:
 * {@code org.pjdbc:type=CircuitBreaker,name=<name>}
 */
public class CircuitBreakerRegistry {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerRegistry.class.getName());
    private static final String JMX_DOMAIN = "org.pjdbc";
    private static final String JMX_ENABLED_PROPERTY = "pjdbc.jmx.enabled";

    private static final CircuitBreakerRegistry INSTANCE = new CircuitBreakerRegistry();

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, ObjectName> registeredMBeans = new ConcurrentHashMap<>();
    private volatile boolean jmxEnabled;

    private CircuitBreakerRegistry() {
        this.jmxEnabled = "true".equalsIgnoreCase(System.getProperty(JMX_ENABLED_PROPERTY, "false"));
    }

    /**
     * Get the singleton registry instance.
     */
    public static CircuitBreakerRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Enable JMX registration for circuit breakers.
     * Also registers any circuit breakers that were created before JMX was enabled.
     */
    public static void enableJmx() {
        INSTANCE.jmxEnabled = true;
        // Register any existing circuit breakers
        for (Map.Entry<String, CircuitBreaker> entry : INSTANCE.circuitBreakers.entrySet()) {
            INSTANCE.registerMBean(entry.getKey(), entry.getValue());
        }
        LOG.info("PJDBC JMX support enabled");
    }

    /**
     * Disable JMX registration and unregister all MBeans.
     */
    public static void disableJmx() {
        INSTANCE.jmxEnabled = false;
        INSTANCE.unregisterAllMBeans();
        LOG.info("PJDBC JMX support disabled");
    }

    /**
     * Check if JMX is enabled.
     */
    public static boolean isJmxEnabled() {
        return INSTANCE.jmxEnabled;
    }

    /**
     * Register a circuit breaker with the registry.
     * If JMX is enabled, also registers an MBean.
     */
    public void register(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();
        circuitBreakers.put(name, circuitBreaker);

        if (jmxEnabled) {
            registerMBean(name, circuitBreaker);
        }

        LOG.fine(() -> "Registered CircuitBreaker: " + name);
    }

    /**
     * Unregister a circuit breaker from the registry.
     */
    public void unregister(String name) {
        circuitBreakers.remove(name);
        unregisterMBean(name);
        LOG.fine(() -> "Unregistered CircuitBreaker: " + name);
    }

    /**
     * Get a circuit breaker by name.
     */
    public CircuitBreaker get(String name) {
        return circuitBreakers.get(name);
    }

    /**
     * Get all registered circuit breaker names.
     */
    public Iterable<String> getNames() {
        return circuitBreakers.keySet();
    }

    /**
     * Get the count of registered circuit breakers.
     */
    public int getCount() {
        return circuitBreakers.size();
    }

    private void registerMBean(String name, CircuitBreaker circuitBreaker) {
        if (registeredMBeans.containsKey(name)) {
            return; // Already registered
        }

        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = createObjectName(name);
            // Wrap in StandardMBean to explicitly specify the interface (avoids naming convention requirement)
            StandardMBean mbean = new StandardMBean(
                new CircuitBreakerMBeanImpl(circuitBreaker), CircuitBreakerMBean.class);
            mbs.registerMBean(mbean, objectName);
            registeredMBeans.put(name, objectName);
            LOG.fine(() -> "Registered JMX MBean: " + objectName);
        } catch (InstanceAlreadyExistsException e) {
            LOG.fine(() -> "MBean already exists: " + name);
        } catch (MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
            LOG.log(Level.WARNING, "Failed to register MBean for CircuitBreaker: " + name, e);
        }
    }

    private void unregisterMBean(String name) {
        ObjectName objectName = registeredMBeans.remove(name);
        if (objectName != null) {
            try {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                mbs.unregisterMBean(objectName);
                LOG.fine(() -> "Unregistered JMX MBean: " + objectName);
            } catch (InstanceNotFoundException e) {
                // Already unregistered
            } catch (MBeanRegistrationException e) {
                LOG.log(Level.WARNING, "Failed to unregister MBean: " + objectName, e);
            }
        }
    }

    private void unregisterAllMBeans() {
        for (String name : registeredMBeans.keySet()) {
            unregisterMBean(name);
        }
    }

    private ObjectName createObjectName(String name) throws MalformedObjectNameException {
        // Escape special characters in name for JMX ObjectName
        String escapedName = name.replace(":", "_").replace("=", "_").replace(",", "_");
        return new ObjectName(JMX_DOMAIN + ":type=CircuitBreaker,name=" + escapedName);
    }

    /**
     * Clear all circuit breakers and MBeans (for testing).
     */
    public void clear() {
        unregisterAllMBeans();
        circuitBreakers.clear();
    }
}
