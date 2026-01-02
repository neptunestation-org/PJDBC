package org.pjdbc.capabilities;

/**
 * Exception thrown when capabilities cannot be loaded or parsed.
 */
public class PjdbcCapabilitiesException extends RuntimeException {

    public PjdbcCapabilitiesException(String message) {
        super(message);
    }

    public PjdbcCapabilitiesException(String message, Throwable cause) {
        super(message, cause);
    }
}
