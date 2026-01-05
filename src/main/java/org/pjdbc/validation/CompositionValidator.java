package org.pjdbc.validation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.pjdbc.capabilities.DriverCapability;
import org.pjdbc.capabilities.PjdbcCapabilities;
import org.pjdbc.sql.JdbcUrlParser;

/**
 * Validates PJDBC driver composition chains.
 *
 * <p>Detects invalid compositions at connection time with helpful error messages.
 *
 * <p>Example usage:
 * <pre>{@code
 * CompositionValidator validator = CompositionValidator.standard();
 * validator.validate("jdbc:cache:jdbc:cache:jdbc:h2:mem:test"); // throws SQLException
 * }</pre>
 */
public final class CompositionValidator {

    private final List<CompositionRule> rules;
    private final PjdbcCapabilities capabilities;

    private static volatile CompositionValidator standardInstance;

    private CompositionValidator(List<CompositionRule> rules, PjdbcCapabilities capabilities) {
        this.rules = List.copyOf(rules);
        this.capabilities = capabilities;
    }

    /**
     * Returns a validator with standard rules.
     */
    public static CompositionValidator standard() {
        if (standardInstance == null) {
            synchronized (CompositionValidator.class) {
                if (standardInstance == null) {
                    standardInstance = builder().build();
                }
            }
        }
        return standardInstance;
    }

    /**
     * Creates a new builder for custom configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates a JDBC URL composition chain.
     *
     * @param url The full JDBC URL
     * @throws SQLException if validation fails with detailed error message
     */
    public void validate(String url) throws SQLException {
        List<ChainEntry> chain = parseChain(url);
        List<String> errors = new ArrayList<>();

        for (CompositionRule rule : rules) {
            rule.validate(chain, url).ifPresent(errors::add);
        }

        if (!errors.isEmpty()) {
            throw new SQLException(formatErrors(url, errors), "08001");
        }
    }

    /**
     * Validates and returns result without throwing.
     */
    public ValidationResult validateQuiet(String url) {
        try {
            List<ChainEntry> chain = parseChain(url);
            List<String> errors = new ArrayList<>();

            for (CompositionRule rule : rules) {
                rule.validate(chain, url).ifPresent(errors::add);
            }

            if (errors.isEmpty()) {
                return ValidationResult.valid();
            } else {
                return ValidationResult.invalid(errors);
            }
        } catch (Exception e) {
            return ValidationResult.invalid(List.of("Parse error: " + e.getMessage()));
        }
    }

    /**
     * Parses a JDBC URL into a chain of driver entries.
     */
    List<ChainEntry> parseChain(String url) {
        List<ChainEntry> chain = new ArrayList<>();
        String current = url;
        int position = 0;

        while (current != null && current.startsWith("jdbc:")) {
            try {
                JdbcUrlParser parser = JdbcUrlParser.parse(current);
                String prefix = parser.getSubprotocol();
                Optional<DriverCapability> capability = capabilities.findByPrefix(prefix);

                String subname = parser.getSubname();
                boolean isTerminal = subname == null || !subname.startsWith("jdbc:");

                chain.add(new ChainEntry(
                    capability.orElse(null),
                    prefix,
                    parser.getParameters(),
                    position,
                    isTerminal
                ));

                current = subname;
                position++;

            } catch (IllegalArgumentException e) {
                // Non-PJDBC URL at end of chain (e.g., h2:mem:test)
                break;
            }
        }

        return chain;
    }

    private String formatErrors(String url, List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid driver composition in URL: ").append(url);

        for (String error : errors) {
            sb.append("\n\n  - ").append(error);
        }

        return sb.toString();
    }

    /**
     * Result of composition validation.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Builder for creating CompositionValidator instances.
     */
    public static class Builder {
        private final List<CompositionRule> rules = new ArrayList<>();
        private PjdbcCapabilities capabilities;

        /**
         * Adds a validation rule.
         */
        public Builder addRule(CompositionRule rule) {
            rules.add(rule);
            return this;
        }

        /**
         * Sets custom capabilities source.
         */
        public Builder capabilities(PjdbcCapabilities caps) {
            this.capabilities = caps;
            return this;
        }

        /**
         * Builds the validator with standard rules if none were added.
         */
        public CompositionValidator build() {
            PjdbcCapabilities caps = capabilities != null ?
                capabilities : PjdbcCapabilities.load();

            if (rules.isEmpty()) {
                // Add standard rules
                rules.add(new TerminalDriverRule());
                rules.add(new ComposableDriverRule());
                rules.add(new ConflictingDriverRule());
                rules.add(new MultiTargetOrderRule());
                rules.add(new CircularChainRule());
            }

            return new CompositionValidator(rules, caps);
        }
    }

    // ====== Built-in Rules ======

    /**
     * Validates that terminal drivers are at the end of the chain.
     */
    static class TerminalDriverRule implements CompositionRule {

        @Override
        public String getName() {
            return "terminal-position";
        }

        @Override
        public Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
            for (ChainEntry entry : chain) {
                if (entry.capability() != null &&
                    entry.capability().terminal() &&
                    !entry.isTerminal()) {

                    return Optional.of(String.format(
                        "Terminal driver '%s' cannot have drivers below it in the chain.\n" +
                        "    Suggestion: Move '%s' to the end of the chain, or remove drivers after it.",
                        entry.prefix(), entry.prefix()));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validates that non-composable drivers are not in the middle of chains.
     */
    static class ComposableDriverRule implements CompositionRule {

        @Override
        public String getName() {
            return "composable";
        }

        @Override
        public Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
            for (ChainEntry entry : chain) {
                boolean isMiddle = entry.position() > 0 && !entry.isTerminal();

                if (entry.capability() != null &&
                    !entry.capability().composable() &&
                    isMiddle) {

                    return Optional.of(String.format(
                        "Driver '%s' is not composable and cannot be used in a chain.\n" +
                        "    Suggestion: Use '%s' as the terminal driver or remove other drivers around it.",
                        entry.prefix(), entry.prefix()));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Detects multiple drivers with conflicting capabilities.
     */
    static class ConflictingDriverRule implements CompositionRule {

        private static final String[][] CONFLICTING = {
            {"caching", "Multiple caching drivers will cause redundant caching"},
            {"pooling", "Multiple pooling drivers will create nested pools"}
        };

        @Override
        public String getName() {
            return "conflicting-capabilities";
        }

        @Override
        public Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
            for (String[] conflict : CONFLICTING) {
                String capability = conflict[0];
                String reason = conflict[1];

                List<String> driversWithCapability = chain.stream()
                    .filter(e -> e.capability() != null && e.capability().hasCapability(capability))
                    .map(ChainEntry::prefix)
                    .toList();

                if (driversWithCapability.size() > 1) {
                    return Optional.of(String.format(
                        "Conflicting %s drivers: %s. %s.\n" +
                        "    Suggestion: Remove all but one %s driver.",
                        capability, String.join(", ", driversWithCapability), reason, capability));
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Validates that stateful single-target drivers don't appear above multi-target drivers.
     */
    static class MultiTargetOrderRule implements CompositionRule {

        private static final List<String> MULTI_TARGET_PREFIXES = List.of(
            "tee", "loadbalance", "federate"
        );

        private static final List<String> SINGLE_TARGET_STATEFUL = List.of(
            "cache", "rediscache", "memcache", "hazelcast",
            "pool", "hikaricp"
        );

        @Override
        public String getName() {
            return "multi-target-order";
        }

        @Override
        public Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
            // Find first multi-target driver
            int multiTargetIndex = -1;
            String multiTargetPrefix = null;

            for (int i = 0; i < chain.size(); i++) {
                if (MULTI_TARGET_PREFIXES.contains(chain.get(i).prefix())) {
                    multiTargetIndex = i;
                    multiTargetPrefix = chain.get(i).prefix();
                    break;
                }
            }

            if (multiTargetIndex < 0) {
                return Optional.empty();
            }

            // Check for problematic single-target drivers above multi-target
            for (int i = 0; i < multiTargetIndex; i++) {
                String prefix = chain.get(i).prefix();
                if (SINGLE_TARGET_STATEFUL.contains(prefix)) {
                    return Optional.of(String.format(
                        "'%s' driver above '%s' may cause unexpected behavior. " +
                        "Caching/pooling drivers maintain per-connection state that doesn't work " +
                        "correctly with multi-target drivers.\n" +
                        "    Suggestion: Move '%s' below '%s' in each target URL, or remove it.",
                        prefix, multiTargetPrefix, prefix, multiTargetPrefix));
                }
            }

            return Optional.empty();
        }
    }

    /**
     * Detects circular or repetitive chains.
     */
    static class CircularChainRule implements CompositionRule {

        // Allow up to 5 consecutive repeats (useful for testing identity drivers)
        private static final int MAX_CONSECUTIVE_REPEATS = 5;
        private static final int MAX_CHAIN_LENGTH = 15;

        @Override
        public String getName() {
            return "circular-chain";
        }

        @Override
        public Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
            // Check for same driver appearing too many times consecutively
            int consecutiveCount = 1;
            String lastPrefix = null;

            for (ChainEntry entry : chain) {
                if (entry.prefix().equals(lastPrefix)) {
                    consecutiveCount++;
                    if (consecutiveCount > MAX_CONSECUTIVE_REPEATS) {
                        return Optional.of(String.format(
                            "Driver '%s' repeated %d times consecutively. " +
                            "This is likely a configuration error.\n" +
                            "    Suggestion: Remove duplicate '%s' drivers.",
                            entry.prefix(), consecutiveCount, entry.prefix()));
                    }
                } else {
                    consecutiveCount = 1;
                    lastPrefix = entry.prefix();
                }
            }

            // Check for excessive chain length
            if (chain.size() > MAX_CHAIN_LENGTH) {
                return Optional.of(String.format(
                    "Chain length of %d is unusually long and may indicate a problem.\n" +
                    "    Suggestion: Review chain composition for unnecessary drivers.",
                    chain.size()));
            }

            return Optional.empty();
        }
    }
}
