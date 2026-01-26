package org.pjdbc.drivers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMapDriverSecurityTest {

    private final ByteArrayOutputStream logContent = new ByteArrayOutputStream();
    private Handler logHandler;
    private Logger driverLogger;

    @TempDir
    File tempDir;

    private ClassLoader isolatedClassLoader;

    @BeforeEach
    void setUp() throws IOException {
        // Programmatically add a handler to capture log output
        driverLogger = Logger.getLogger("org.pjdbc.drivers.UserMapDriver");
        logHandler = new StreamHandler(logContent, new SimpleFormatter());
        driverLogger.addHandler(logHandler);

        // Create a dummy properties file in the temp directory
        File propertiesFile = new File(tempDir, "org.pjdbc.UserMapDriver.UserMapFile");
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            writer.write("testuser=dbuser/dbpass");
        }

        // Create a classloader that includes the temp directory and the main classpath
        URL[] urls = {
            tempDir.toURI().toURL(),
            // Assuming maven structure, this points to the compiled classes
            new File("target/classes").toURI().toURL()
        };
        isolatedClassLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent());
    }

    @AfterEach
    void tearDown() {
        // Remove the custom handler to avoid affecting other tests
        if (driverLogger != null && logHandler != null) {
            driverLogger.removeHandler(logHandler);
        }
    }

    @Test
    void testSecurityWarningIsLogged() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(isolatedClassLoader);

            // Load the driver class using the isolated classloader to trigger static initializer
            Class.forName("org.pjdbc.drivers.UserMapDriver", true, isolatedClassLoader);

            // Flush the handler to ensure all log records are written
            logHandler.flush();

            // Check if the warning was logged
            String loggedOutput = logContent.toString();
            assertTrue(loggedOutput.contains("CRITICAL SECURITY WARNING"), "Expected security warning was not logged.");
            assertTrue(loggedOutput.contains("Loading database credentials from the plaintext file"),
                "Log message should contain details about plaintext file.");
            assertTrue(loggedOutput.contains("org.pjdbc.UserMapDriver.UserMapFile"),
                "Log message should mention the properties file name.");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
