package org.pjdbc.drivers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserMapDriverTest {

    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    @BeforeEach
    public void setUpStreams() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setErr(originalErr);
    }

    @Test
    public void testSecurityWarningIsPrinted() throws Exception {
        // Use a custom classloader to ensure the static initializer is re-run
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{UserMapDriver.class.getProtectionDomain().getCodeSource().getLocation()},
            ClassLoader.getSystemClassLoader().getParent()
        );

        // Load the class to trigger the static initializer
        Class.forName("org.pjdbc.drivers.UserMapDriver", true, classLoader);

        String output = errContent.toString();
        assertTrue(output.contains("SECURITY WARNING"));
        assertTrue(output.contains("CRITICAL security vulnerability"));
    }
}
