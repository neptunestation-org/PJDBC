package org.pjdbc.sql;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class JdbcUrlParserProperties {

    public static class JdbcUrlGenerator extends Generator<String> {
        private static final String ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private static final String ALPHA_NUMERIC = ALPHA + "0123456789";

        public JdbcUrlGenerator() {
            super(String.class);
        }

        private String randomString(SourceOfRandomness random, int min, int max) {
            int length = random.nextInt(min, max);
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(ALPHA_NUMERIC.charAt(random.nextInt(ALPHA_NUMERIC.length())));
            }
            return sb.toString();
        }

        private String randomSubprotocol(SourceOfRandomness random, int min, int max) {
            int length = random.nextInt(min, max);
            StringBuilder sb = new StringBuilder(length);
            sb.append(ALPHA.charAt(random.nextInt(ALPHA.length())));
            for (int i = 1; i < length; i++) {
                sb.append(ALPHA_NUMERIC.charAt(random.nextInt(ALPHA_NUMERIC.length())));
            }
            return sb.toString();
        }

        @Override
        public String generate(SourceOfRandomness random, GenerationStatus status) {
            String subprotocol = randomSubprotocol(random, 5, 10);
            String subname = "jdbc:mock:" + randomString(random, 3, 8);
            int numParams = random.nextInt(0, 5);
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < numParams; i++) {
                params.put(randomString(random, 3, 8), randomString(random, 3, 8));
            }

            StringBuilder url = new StringBuilder("jdbc:");
            url.append(subprotocol);
            if (!params.isEmpty()) {
                url.append("[");
                url.append(params.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(",")));
                url.append("]");
            }
            url.append(":").append(subname);
            return url.toString();
        }
    }

    @Property
    public void parsedUrlShouldBeTheSameAsOriginal(@From(JdbcUrlGenerator.class) String url) {
        assertEquals(url, JdbcUrlParser.parse(url).toString());
    }
}
