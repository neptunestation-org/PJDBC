package org.pjdbc.validation;

import java.util.Map;

import org.pjdbc.capabilities.DriverCapability;

/**
 * Represents one driver in a PJDBC composition chain.
 *
 * @param capability Driver metadata from PjdbcCapabilities, null if unknown (e.g., postgresql)
 * @param prefix The URL subprotocol (e.g., "cache", "log", "pool")
 * @param parameters URL parameters for this driver
 * @param position Position in chain (0 = outermost, increases inward)
 * @param isTerminal True if this is the innermost (terminal) driver
 */
public record ChainEntry(
    DriverCapability capability,
    String prefix,
    Map<String, String> parameters,
    int position,
    boolean isTerminal
) {}
