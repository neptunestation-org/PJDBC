package org.pjdbc.drivers;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class DriverCompositionQuickCheckTest {

    static {
        try {
            Class.forName("org.pjdbc.drivers.CatDriver");
            Class.forName("org.pjdbc.drivers.LogDriver");
            Class.forName("org.pjdbc.drivers.FilterDriver");
            Class.forName("org.pjdbc.drivers.ReadonlyDriver");
            Class.forName("org.pjdbc.drivers.SinkDriver");
            Class.forName("org.pjdbc.drivers.RetryDriver");
            Class.forName("org.pjdbc.drivers.SerialDriver");
            Class.forName("org.pjdbc.drivers.TracingDriver");
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<String> DRIVERS = Arrays.asList(
            "cat", "log", "filter", "readonly", "sink", "retry", "serial", "trace");

    public static class DriverChainGenerator extends Generator<List<String>> {
        public DriverChainGenerator(Class<List<String>> type) {
            super(type);
        }

        @Override
        public List<String> generate(SourceOfRandomness random, com.pholser.junit.quickcheck.generator.GenerationStatus status) {
            List<String> driverChain = new ArrayList<>(DRIVERS);
            Collections.shuffle(driverChain, random.toJDKRandom());
            int size = 1 + random.nextInt(DRIVERS.size());
            return driverChain.subList(0, size);
        }
    }

    @Property(trials = 500)
    public void testDriverComposition(@From(DriverChainGenerator.class) List<String> driverChain) throws Exception {
        String url = "jdbc:" + String.join(":jdbc:", driverChain) + ":jdbc:h2:mem:test";
        try (Connection conn = DriverManager.getConnection(url)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");

            if (driverChain.contains("sink")) {
                assertNull(rs);
            } else {
                assertNotNull(rs);
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
