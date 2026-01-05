package org.pjdbc.validation;

import java.util.List;
import java.util.Optional;

/**
 * Interface for driver composition validation rules.
 *
 * <p>Each rule checks one aspect of composition validity and returns
 * an error message if validation fails.
 */
public interface CompositionRule {

    /**
     * Validate the driver chain.
     *
     * @param chain List of ChainEntry from outermost to innermost driver
     * @param rawUrl The original URL for error messages
     * @return Empty if valid, error message if invalid
     */
    Optional<String> validate(List<ChainEntry> chain, String rawUrl);

    /**
     * Rule name for logging and configuration.
     */
    String getName();
}
