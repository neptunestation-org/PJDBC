package org.pjdbc.properties;

import net.jqwik.api.*;
import org.pjdbc.sql.JdbcUrlParser;
import org.pjdbc.testing.PjdbcArbitraries;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for JDBC URL parsing.
 */
class UrlParsingProperties {

    private static final String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHA_NUMERIC = ALPHA + "0123456789";

    /**
     * Round-trip property: parse(url).toString() == url
     */
    @Property(tries = 100)
    void parseRoundTrip(@ForAll("validJdbcUrls") String url) {
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);
        assertEquals(url, parsed.toString(),
            "URL should survive parse round-trip");
    }

    /**
     * Protocol is always "jdbc".
     */
    @Property(tries = 50)
    void protocolIsAlwaysJdbc(@ForAll("validJdbcUrls") String url) {
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);
        assertEquals("jdbc", parsed.getProtocol());
    }

    /**
     * Subprotocol is preserved.
     */
    @Property(tries = 50)
    void subprotocolIsPreserved(
            @ForAll("drivers") String driver,
            @ForAll("mockUrls") String terminal) {

        String url = "jdbc:" + driver + ":" + terminal;
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);
        assertEquals(driver, parsed.getSubprotocol());
    }

    /**
     * Subname is preserved.
     */
    @Property(tries = 50)
    void subnameIsPreserved(
            @ForAll("drivers") String driver,
            @ForAll("mockUrls") String terminal) {

        String url = "jdbc:" + driver + ":" + terminal;
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);
        assertEquals(terminal, parsed.getSubname());
    }

    /**
     * Parameters are correctly extracted.
     */
    @Property(tries = 30)
    void parametersAreExtracted(
            @ForAll("drivers") String driver,
            @ForAll("paramMaps") Map<String, String> params,
            @ForAll("mockUrls") String terminal) {

        StringBuilder url = new StringBuilder("jdbc:");
        url.append(driver);
        if (!params.isEmpty()) {
            url.append("[");
            url.append(params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",")));
            url.append("]");
        }
        url.append(":").append(terminal);

        JdbcUrlParser parsed = JdbcUrlParser.parse(url.toString());
        assertEquals(params, parsed.getParameters());
    }

    /**
     * Individual parameter retrieval works.
     */
    @Property(tries = 30)
    void individualParameterRetrieval(
            @ForAll("drivers") String driver,
            @ForAll("paramKey") String key,
            @ForAll("paramValue") String value,
            @ForAll("mockUrls") String terminal) {

        String url = "jdbc:" + driver + "[" + key + "=" + value + "]:" + terminal;
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);

        assertEquals(value, parsed.getParameter(key));
        assertNull(parsed.getParameter("nonexistent"));
        assertEquals("default", parsed.getParameter("nonexistent", "default"));
    }

    /**
     * URLs without parameters have empty parameter map.
     */
    @Property(tries = 50)
    void noParametersGivesEmptyMap(
            @ForAll("drivers") String driver,
            @ForAll("mockUrls") String terminal) {

        String url = "jdbc:" + driver + ":" + terminal;
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.getParameters().isEmpty());
    }

    /**
     * Null URLs throw IllegalArgumentException.
     */
    @Example
    void nullUrlThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> JdbcUrlParser.parse(null));
    }

    /**
     * Invalid URLs throw IllegalArgumentException.
     */
    @Property(tries = 50)
    void invalidUrlsThrow(@ForAll("invalidUrls") String url) {
        assertThrows(IllegalArgumentException.class,
            () -> JdbcUrlParser.parse(url),
            "Should throw for invalid URL: " + url);
    }

    /**
     * Empty parameter brackets are handled.
     */
    @Property(tries = 20)
    void emptyBracketsGivesEmptyParams(
            @ForAll("drivers") String driver,
            @ForAll("mockUrls") String terminal) {

        String url = "jdbc:" + driver + "[]:" + terminal;
        JdbcUrlParser parsed = JdbcUrlParser.parse(url);

        assertTrue(parsed.getParameters().isEmpty());
    }

    // ========== ARBITRARY PROVIDERS ==========

    @Provide
    Arbitrary<String> drivers() {
        return Arbitraries.of(PjdbcArbitraries.ALL_DRIVERS);
    }

    @Provide
    Arbitrary<String> validJdbcUrls() {
        return Combinators.combine(
            Arbitraries.of(PjdbcArbitraries.COMPOSABLE_DRIVERS),
            paramMaps(),
            PjdbcArbitraries.mockUrls()
        ).as((driver, params, terminal) -> {
            StringBuilder url = new StringBuilder("jdbc:");
            url.append(driver);
            if (!params.isEmpty()) {
                url.append("[");
                url.append(params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")));
                url.append("]");
            }
            url.append(":").append(terminal);
            return url.toString();
        });
    }

    @Provide
    Arbitrary<Map<String, String>> paramMaps() {
        return PjdbcArbitraries.paramMaps();
    }

    @Provide
    Arbitrary<String> mockUrls() {
        return PjdbcArbitraries.mockUrls();
    }

    @Provide
    Arbitrary<String> paramKey() {
        return Arbitraries.strings()
            .withChars(ALPHA_NUMERIC.toCharArray())
            .ofMinLength(3)
            .ofMaxLength(8)
            .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> paramValue() {
        return Arbitraries.strings()
            .withChars(ALPHA_NUMERIC.toCharArray())
            .ofMinLength(1)
            .ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> invalidUrls() {
        return Arbitraries.of(
            "",
            "not-a-url",
            "jdbc:",
            "jdbc::",
            "http://example.com",
            "jdbc:1invalid:foo",  // subprotocol can't start with digit
            "foobar",
            "jdbc",
            ":mock:foo"
        );
    }
}
