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
            List<ChainEntry> chain = validator.parseChain("jdbc:log:jdbc:h2:mem:test");

            // Chain includes both log and h2
            assertEquals(2, chain.size());
            assertEquals("log", chain.get(0).prefix());
            assertEquals("h2", chain.get(1).prefix());
            assertEquals(0, chain.get(0).position());
            assertFalse(chain.get(0).isTerminal());
            assertTrue(chain.get(1).isTerminal());
        }

        @Test
        @DisplayName("parses multi-driver chain")
        void parsesMultiDriverChain() {
            List<ChainEntry> chain = validator.parseChain("jdbc:log:jdbc:cache:jdbc:pool:jdbc:h2:mem:test");

            // Chain includes all four drivers: log, cache, pool, h2
            assertEquals(4, chain.size());
            assertEquals("log", chain.get(0).prefix());
            assertEquals("cache", chain.get(1).prefix());
            assertEquals("pool", chain.get(2).prefix());
            assertEquals("h2", chain.get(3).prefix());
            assertFalse(chain.get(0).isTerminal());
            assertFalse(chain.get(1).isTerminal());
            assertFalse(chain.get(2).isTerminal());
            assertTrue(chain.get(3).isTerminal());
        }

        @Test
        @DisplayName("parses chain with parameters")
        void parsesChainWithParameters() {
            List<ChainEntry> chain = validator.parseChain("jdbc:cache[ttl=60,maxSize=100]:jdbc:h2:mem:test");

            // Chain includes cache and h2
            assertEquals(2, chain.size());
            assertEquals("cache", chain.get(0).prefix());
            assertEquals("60", chain.get(0).parameters().get("ttl"));
            assertEquals("100", chain.get(0).parameters().get("maxSize"));
        }

        @Test
        @DisplayName("looks up driver capabilities")
        void looksUpDriverCapabilities() {
            List<ChainEntry> chain = validator.parseChain("jdbc:cache:jdbc:h2:mem:test");

            assertNotNull(chain.get(0).capability());
            assertEquals("cache", chain.get(0).capability().prefix());
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
                validator.validate("jdbc:log:jdbc:mock:foo"));
        }

        @Test
        @DisplayName("passes when sink driver is actually at end")
        void passesWhenSinkDriverAtEnd() {
            // sink at the end of the chain (nothing after it)
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:sink:foo"));
        }

        @Test
        @DisplayName("fails when terminal driver is in middle")
        void failsWhenTerminalDriverInMiddle() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:mock:jdbc:log:jdbc:h2:mem:test"));

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
                validator.validate("jdbc:log:jdbc:cache:jdbc:pool:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("passes when non-composable driver is at end")
        void passesWhenNonComposableDriverAtEnd() {
            // mock is non-composable but it's at the end
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:mock:foo"));
        }
    }

    @Nested
    @DisplayName("Conflicting Driver Rule")
    class ConflictingDriverRuleTests {

        @Test
        @DisplayName("passes with single caching driver")
        void passesWithSingleCachingDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:cache:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("fails with multiple caching drivers")
        void failsWithMultipleCachingDrivers() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cache:jdbc:cache:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Conflicting"));
            assertTrue(ex.getMessage().contains("caching"));
        }

        @Test
        @DisplayName("passes with single pooling driver")
        void passesWithSinglePoolingDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:pool:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("fails with multiple pooling drivers")
        void failsWithMultiplePoolingDrivers() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:pool:jdbc:hikaricp:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Conflicting"));
            assertTrue(ex.getMessage().contains("pooling"));
        }
    }

    @Nested
    @DisplayName("Multi-Target Order Rule")
    class MultiTargetOrderRuleTests {

        @Test
        @DisplayName("passes when cache is below tee")
        void passesWhenCacheIsBelowTee() {
            // This URL format is valid - cache is in the target URLs, not above tee
            assertDoesNotThrow(() ->
                validator.validate("jdbc:tee:jdbc:cache:jdbc:h2:mem:a;jdbc:cache:jdbc:h2:mem:b"));
        }

        @Test
        @DisplayName("fails when cache is above tee")
        void failsWhenCacheIsAboveTee() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cache:jdbc:tee:jdbc:h2:mem:a;jdbc:h2:mem:b"));

            assertTrue(ex.getMessage().contains("cache"));
            assertTrue(ex.getMessage().contains("tee"));
        }

        @Test
        @DisplayName("fails when pool is above loadbalance")
        void failsWhenPoolIsAboveLoadbalance() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:pool:jdbc:loadbalance:jdbc:h2:mem:a;jdbc:h2:mem:b"));

            assertTrue(ex.getMessage().contains("pool"));
            assertTrue(ex.getMessage().contains("loadbalance"));
        }

        @Test
        @DisplayName("passes when log is above tee")
        void passesWhenLogIsAboveTee() {
            // log is stateless, so it's fine above multi-target drivers
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:tee:jdbc:h2:mem:a;jdbc:h2:mem:b"));
        }
    }

    @Nested
    @DisplayName("Circular Chain Rule")
    class CircularChainRuleTests {

        @Test
        @DisplayName("passes for single driver")
        void passesForSingleDriver() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("passes for five consecutive same drivers")
        void passesForFiveConsecutiveSameDrivers() {
            // Up to 5 consecutive is allowed (for testing identity drivers)
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:log:jdbc:log:jdbc:log:jdbc:log:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("fails for six consecutive same drivers")
        void failsForSixConsecutiveSameDrivers() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:log:jdbc:log:jdbc:log:jdbc:log:jdbc:log:jdbc:log:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("log"));
            assertTrue(ex.getMessage().contains("repeated"));
        }

        @Test
        @DisplayName("passes for different drivers")
        void passesForDifferentDrivers() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:cache:jdbc:pool:jdbc:retry:jdbc:h2:mem:test"));
        }
    }

    @Nested
    @DisplayName("Valid Compositions")
    class ValidCompositionTests {

        @Test
        @DisplayName("accepts log:cache:pool chain")
        void acceptsLogCachePoolChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:log:jdbc:cache:jdbc:pool:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts retry:cache chain")
        void acceptsRetryCacheChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:retry:jdbc:cache:jdbc:h2:mem:test"));
        }

        @Test
        @DisplayName("accepts metrics:tracing:log chain")
        void acceptsMetricsTracingLogChain() {
            assertDoesNotThrow(() ->
                validator.validate("jdbc:metrics:jdbc:tracing:jdbc:log:jdbc:h2:mem:test"));
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
                validator.validateQuiet("jdbc:log:jdbc:h2:mem:test");

            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("validateQuiet returns invalid for bad composition")
        void validateQuietReturnsInvalidForBadComposition() {
            CompositionValidator.ValidationResult result =
                validator.validateQuiet("jdbc:cache:jdbc:cache:jdbc:h2:mem:test");

            assertFalse(result.isValid());
            assertFalse(result.getErrors().isEmpty());
            assertTrue(result.getErrors().get(0).contains("Conflicting"));
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
                customValidator.validate("jdbc:log:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Always fails"));
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessageTests {

        @Test
        @DisplayName("includes URL in error message")
        void includesUrlInErrorMessage() {
            String url = "jdbc:cache:jdbc:cache:jdbc:h2:mem:test";

            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate(url));

            assertTrue(ex.getMessage().contains(url));
        }

        @Test
        @DisplayName("includes suggestion in error message")
        void includesSuggestionInErrorMessage() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cache:jdbc:cache:jdbc:h2:mem:test"));

            assertTrue(ex.getMessage().contains("Suggestion"));
        }

        @Test
        @DisplayName("uses standard SQL state for connection errors")
        void usesStandardSqlState() {
            SQLException ex = assertThrows(SQLException.class, () ->
                validator.validate("jdbc:cache:jdbc:cache:jdbc:h2:mem:test"));

            assertEquals("08001", ex.getSQLState());
        }
    }
}
