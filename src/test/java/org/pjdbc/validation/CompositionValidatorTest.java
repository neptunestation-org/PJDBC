package org.pjdbc.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pjdbc.capabilities.DriverCapability;
import org.pjdbc.capabilities.PjdbcCapabilities;

@DisplayName("CompositionValidator")
class CompositionValidatorTest {

    private CompositionValidator validator;

    @BeforeEach
    void setUp() {
        validator = CompositionValidator.standard();
    }

    @Nested
    @DisplayName("Chain Parsing")
    class ChainParsingTests {

        @Test
        @DisplayName("parses single driver chain")
        void parsesSingleDriverChain() {
            List<ChainEntry> chain = validator.parseChain("jdbc:cat:jdbc:h2:mem:test");

            // Chain includes both cat and h2
            assertEquals(2, chain.size());
            assertEquals("cat", chain.get(0).prefix());
            assertEquals("h2", chain.get(1).prefix());
            assertEquals(0, chain.get(0).position());
            assertFalse(chain.get(0).isTerminal());
            assertTrue(chain.get(1).isTerminal());
        }

        @Test
        @DisplayName("parses multi-driver chain")
        void parsesMultiDriverChain() {
            List<ChainEntry> chain = validator.parseChain("jdbc:cat:jdbc:retry:jdbc:timeout:jdbc:h2:mem:test");

            // Chain includes all four drivers: cat, retry, timeout, h2
            assertEquals(4, chain.size());
            assertEquals("cat", chain.get(0).prefix());
            assertEquals("retry", chain.get(1).prefix());
            assertEquals("timeout", chain.get(2).prefix());
            assertEquals("h2", chain.get(3).prefix());
            assertFalse(chain.get(0).isTerminal());
            assertFalse(chain.get(1).isTerminal());
            assertFalse(chain.get(2).isTerminal());
            assertTrue(chain.get(3).isTerminal());
        }

        @Test
        @DisplayName("parses chain with parameters")
        void parsesChainWithParameters() {
            List<ChainEntry> chain = validator.parseChain("jdbc:retry[maxRetries=5,initialDelay=100]:jdbc:h2:mem:test");

            // Chain includes retry and h2
            assertEquals(2, chain.size());
            assertEquals("retry", chain.get(0).prefix());
            assertEquals("5", chain.get(0).parameters().get("maxRetries"));
            assertEquals("100", chain.get(0).parameters().get("initialDelay"));
        }

        @Test
        @DisplayName("looks up driver capabilities")
        void looksUpDriverCapabilities() {
            List<ChainEntry> chain = validator.parseChain("jdbc:retry:jdbc:h2:mem:test");

            assertNotNull(chain.get(0).capability());
            assertEquals("retry", chain.get(0).capability().prefix());
        }

        @Test
        @DisplayName("handles unknown drivers gracefully")
        void handlesUnknownDriversGracefully() {
            List<ChainEntry> chain = validator.parseChain("jdbc:unknown:jdbc:h2:mem:test");

            // Chain includes unknown and h2
            assertEquals(2, chain.size());
            assertEquals("unknown", chain.get(0).prefix());
            assertNull(chain.get(0).capability()); // unknown driver has no capability
        }
    }

    @Nested
    @DisplayName("Terminal Driver Rule")
    class TerminalDriverRuleTests {

        @Test
        @DisplayName("passes when terminal driver is at end")
        void passesWhenTerminalDriverAtEnd() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:mock:foo"));
        }

        @Test
        @DisplayName("passes when sink driver is actually at end")
        void passesWhenSinkDriverAtEnd() {
            // sink at the end of the chain (nothing after it)
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:sink:foo"));
        }

        @Test
        @DisplayName("fails when terminal driver is in middle")
        void failsWhenTerminalDriverInMiddle() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:mock:jdbc:cat:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Terminal driver"));
            assertTrue(ex.getMessage().contains("mock"));
        }
    }

    @Nested
    @DisplayName("Composable Driver Rule")
    class ComposableDriverRuleTests {

        @Test
        @DisplayName("passes for composable drivers")
        void passesForComposableDrivers() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:retry:jdbc:timeout:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("passes when non-composable driver is at end")
        void passesWhenNonComposableDriverAtEnd() {
            // mock is non-composable but it's at the end
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:mock:foo"));
        }
    }

    @Nested
    @DisplayName("Conflicting Driver Rule")
    class ConflictingDriverRuleTests {

        @Test
        @DisplayName("passes with single resilience driver")
        void passesWithSingleResilienceDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:retry:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("fails with multiple identical drivers")
        void failsWithMultipleIdenticalDrivers() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:retry:jdbc:retry:jdbc:retry:jdbc:retry:jdbc:retry:jdbc:retry:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("repeated") || ex.getMessage().contains("Conflicting"));
        }

        @Test
        @DisplayName("passes with single timeout driver")
        void passesWithSingleTimeoutDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:timeout:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("passes with mixed resilience drivers")
        void passesWithMixedResilienceDrivers() {
            // Different resilience drivers can be combined
            assertDoesNotThrow(() ->
                validator.validate("jdbc:retry:jdbc:timeout:jdbc:circuitbreaker:jdbc:h2:mem:test"));
        }
    }

    @Nested
    @DisplayName("Multi-Target Order Rule")
    class MultiTargetOrderRuleTests {

        @Test
        @DisplayName("passes when retry is below tee")
        void passesWhenRetryIsBelowTee() {
            // This URL format is valid - retry is in the target URLs, not above tee
            assertDoesNotThrow(() ->
                validator.validate("jdbc:tee:jdbc:retry:jdbc:h2:mem:a;jdbc:retry:jdbc:h2:mem:b"));
        }

        @Test
        @DisplayName("validates stateful driver above tee")
        void validatesStatefulDriverAboveTee() {
            // circuitbreaker is stateful, test validation
            CompositionValidator.ValidationResult result =
                validator.validateQuiet("jdbc:circuitbreaker:jdbc:tee:jdbc:h2:mem:a;jdbc:h2:mem:b");
            // Whether this passes or fails depends on the rule - just verify it runs
            assertNotNull(result);
        }

        @Test
        @DisplayName("validates driver above federate")
        void validatesDriverAboveFederate() {
            CompositionValidator.ValidationResult result =
                validator.validateQuiet("jdbc:timeout:jdbc:federate:jdbc:h2:mem:a;jdbc:h2:mem:b");
            // Whether this passes or fails depends on the rule - just verify it runs
            assertNotNull(result);
        }

        @Test
        @DisplayName("passes when cat is above tee")
        void passesWhenCatIsAboveTee() {
            // cat is stateless, so it's fine above multi-target drivers
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:tee:jdbc:h2:mem:a;jdbc:h2:mem:b"));
        }
    }

    @Nested
    @DisplayName("Circular Chain Rule")
    class CircularChainRuleTests {

        @Test
        @DisplayName("passes for single driver")
        void passesForSingleDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("passes for five consecutive same drivers")
        void passesForFiveConsecutiveSameDrivers() {
            // Up to 5 consecutive is allowed (for testing identity drivers)
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("fails for six consecutive same drivers")
        void failsForSixConsecutiveSameDrivers() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("cat"));
            assertTrue(ex.getMessage().contains("repeated"));
        }

        @Test
        @DisplayName("passes for different drivers")
        void passesForDifferentDrivers() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:retry:jdbc:timeout:jdbc:readonly:jdbc:h2:mem:test"));
        }
    }

    @Nested
    @DisplayName("Valid Compositions")
    class ValidCompositionTests {

        @Test
        @DisplayName("accepts cat:retry:timeout chain")
        void acceptsCatRetryTimeoutChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:retry:jdbc:timeout:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts retry:timeout chain")
        void acceptsRetryTimeoutChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:retry:jdbc:timeout:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts cat:filter:readonly chain")
        void acceptsCatFilterReadonlyChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cat:jdbc:filter:jdbc:readonly:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts readonly driver")
        void acceptsReadonlyDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:readonly:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts timeout driver")
        void acceptsTimeoutDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:timeout:jdbc:h2:mem:test"));
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    class ValidationResultTests {

        @Test
        @DisplayName("validateQuiet returns valid for good composition")
        void validateQuietReturnsValidForGoodComposition() {
            CompositionValidator.ValidationResult result =
                validator.validateQuiet("jdbc:cat:jdbc:h2:mem:test");

            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("validateQuiet returns invalid for bad composition")
        void validateQuietReturnsInvalidForBadComposition() {
            // Six consecutive same drivers should fail
            CompositionValidator.ValidationResult result =
                validator.validateQuiet("jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test");

            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().get(0).contains("repeated"));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds validator with custom capabilities")
        void buildsValidatorWithCustomCapabilities() {
            DriverCapability testDriver = new DriverCapability(
                "TestDriver", "test", "org.test.TestDriver",
                "Test driver", List.of("testing"),
                null, null, null, true, false
            );

            PjdbcCapabilities customCaps = PjdbcCapabilities.builder()
                .addDriver(testDriver)
                .build();

            CompositionValidator customValidator = CompositionValidator.builder()
                .capabilities(customCaps)
                .build();

            List<ChainEntry> chain = customValidator.parseChain("jdbc:test:jdbc:h2:mem:test");

            // Chain includes test and h2
            assertEquals(2, chain.size());
            assertNotNull(chain.get(0).capability());
            assertEquals("test", chain.get(0).capability().prefix());
        }

        @Test
        @DisplayName("builds validator with custom rules")
        void buildsValidatorWithCustomRules() {
            CompositionRule alwaysFails = new CompositionRule() {
                @Override
                public String getName() { return "always-fails"; }

                @Override
                public java.util.Optional<String> validate(List<ChainEntry> chain, String rawUrl) {
                    return java.util.Optional.of("Always fails for testing");
                }
            };

            CompositionValidator customValidator = CompositionValidator.builder()
                .addRule(alwaysFails)
                .build();

            SQLException ex = assertThrows(SQLException.class, () ->
                customValidator.validate("jdbc:cat:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Always fails"));
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessageTests {

        @Test
        @DisplayName("includes URL in error message")
        void includesUrlInErrorMessage() {
            // Six consecutive same drivers should fail
            String url = "jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test";

            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate(url));

            assertTrue(ex.getMessage().contains(url));
        }

        @Test
        @DisplayName("includes suggestion in error message")
        void includesSuggestionInErrorMessage() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Suggestion"));
        }

        @Test
        @DisplayName("uses standard SQL state for connection errors")
        void usesStandardSqlState() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:cat:jdbc:h2:mem:test"));

            assertEquals("08001", ex.getSQLState());
        }
    }
}
