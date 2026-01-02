package org.pjdbc.sql;

import net.jqwik.api.*;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for JdbcUrlParser using jqwik.
 * Migrated from junit-quickcheck.
 */
class JdbcUrlParserProperties {

    private static final String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHA_NUMERIC = ALPHA + "0123456789";

    @Property
    void parsedUrlShouldBeTheSameAsOriginal(@ForAll("validJdbcUrls") String url) {
        assertEquals(url, JdbcUrlParser.parse(url).toString());
    }

    @Provide
    Arbitrary<String> validJdbcUrls() {
        Arbitrary<String> subprotocol = Arbitraries.strings()
            .withChars(ALPHA_NUMERIC.toCharArray())
            .ofMinLength(5)
            .ofMaxLength(10)
            .filter(s -> !s.isEmpty() && Character.isLetter(s.charAt(0)));

        Arbitrary<String> subname = Arbitraries.strings()
            .withChars(ALPHA_NUMERIC.toCharArray())
            .ofMinLength(3)
            .ofMaxLength(8)
            .map(s -> "jdbc:mock:" + s);

        Arbitrary<Map<String, String>> params = Arbitraries.maps(
            Arbitraries.strings().withChars(ALPHA_NUMERIC.toCharArray()).ofMinLength(3).ofMaxLength(8),
            Arbitraries.strings().withChars(ALPHA_NUMERIC.toCharArray()).ofMinLength(3).ofMaxLength(8)
        ).ofMinSize(0).ofMaxSize(5);

        return Combinators.combine(subprotocol, params, subname)
            .as((sp, pm, sn) -> {
                StringBuilder url = new StringBuilder("jdbc:");
                url.append(sp);
                if (!pm.isEmpty()) {
                    url.append("[");
                    url.append(pm.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(",")));
                    url.append("]");
                }
                url.append(":").append(sn);
                return url.toString();
            });
    }
}
