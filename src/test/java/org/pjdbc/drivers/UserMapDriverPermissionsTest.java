package org.pjdbc.drivers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
public class UserMapDriverPermissionsTest {

    @TempDir
    Path tempDir;

    private CapturingLogHandler logHandler;

    @BeforeEach
    public void setUp() {
        logHandler = new CapturingLogHandler();
        Logger.getLogger(UserMapDriver.class.getName()).addHandler(logHandler);
        Logger.getLogger(UserMapDriver.class.getName()).setLevel(Level.ALL);
    }

    @AfterEach
    public void tearDown() {
        Logger.getLogger(UserMapDriver.class.getName()).removeHandler(logHandler);
    }

    @Test
    public void testInsecurePermissionsTriggerWarning() throws Exception {
        Path userMapFile = createTempUserMapFile("insecure.properties");
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.GROUP_READ); // Insecure permission
        perms.add(PosixFilePermission.OTHERS_READ); // Insecure permission
        Files.setPosixFilePermissions(userMapFile, perms);

        loadUserMapDriverInIsolation();

        assertTrue(logHandler.hasLog("CRITICAL SECURITY WARNING"));
    }

    @Test
    public void testSecurePermissionsDoNotTriggerWarning() throws Exception {
        Path userMapFile = createTempUserMapFile("secure.properties");
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(userMapFile, perms);

        loadUserMapDriverInIsolation();

        assertEquals(0, logHandler.count);
    }

    private Path createTempUserMapFile(String filename) throws IOException {
        Path file = tempDir.resolve("org.pjdbc.UserMapDriver.UserMapFile");
        Files.write(file, "app_user=db_user/db_password".getBytes());
        return file;
    }

    private void loadUserMapDriverInIsolation() throws Exception {
        URL classesUrl = UserMapDriver.class.getProtectionDomain().getCodeSource().getLocation();
        URL testClassesUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        URL tempDirUrl = tempDir.toUri().toURL();

        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{tempDirUrl, classesUrl, testClassesUrl},
            this.getClass().getClassLoader().getParent()
        );

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            Class.forName(UserMapDriver.class.getName(), true, classLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static class CapturingLogHandler extends Handler {
        private String lastMessage = "";
        private int count = 0;

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.SEVERE) {
                lastMessage = record.getMessage();
                count++;
            }
        }

        public boolean hasLog(String message) {
            return lastMessage.contains(message);
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}
