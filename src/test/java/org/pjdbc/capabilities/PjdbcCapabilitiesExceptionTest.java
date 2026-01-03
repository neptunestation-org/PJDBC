package org.pjdbc.capabilities;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

/**
 * Unit tests for PjdbcCapabilitiesException.
 */
public class PjdbcCapabilitiesExceptionTest {

    // ========== Constructor with message only ==========

    @Test
    public void testConstructorWithMessage() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test error");
        assertEquals("Test error", ex.getMessage());
    }

    @Test
    public void testConstructorWithMessageNoCause() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test error");
        assertNull(ex.getCause());
    }

    @Test
    public void testConstructorWithEmptyMessage() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("");
        assertEquals("", ex.getMessage());
    }

    @Test
    public void testConstructorWithNullMessage() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(null);
        assertNull(ex.getMessage());
    }

    // ========== Constructor with message and cause ==========

    @Test
    public void testConstructorWithMessageAndCause() {
        IOException cause = new IOException("IO failed");
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Failed to load", cause);

        assertEquals("Failed to load", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    public void testConstructorWithNullCause() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Error", null);

        assertEquals("Error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void testConstructorWithBothNull() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(null, null);

        assertNull(ex.getMessage());
        assertNull(ex.getCause());
    }

    // ========== Exception chaining ==========

    @Test
    public void testExceptionChaining() {
        IllegalArgumentException root = new IllegalArgumentException("Invalid arg");
        IOException middle = new IOException("IO error", root);
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Capabilities failed", middle);

        assertEquals("Capabilities failed", ex.getMessage());
        assertSame(middle, ex.getCause());
        assertSame(root, ex.getCause().getCause());
    }

    @Test
    public void testCauseMessagePreserved() {
        RuntimeException cause = new RuntimeException("Original error message");
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Wrapper", cause);

        assertEquals("Original error message", ex.getCause().getMessage());
    }

    // ========== Inheritance tests ==========

    @Test
    public void testIsRuntimeException() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test");
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    public void testIsException() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test");
        assertTrue(ex instanceof Exception);
    }

    @Test
    public void testIsThrowable() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test");
        assertTrue(ex instanceof Throwable);
    }

    @Test
    public void testCanBeThrownAsRuntimeException() {
        try {
            throw new PjdbcCapabilitiesException("Test throw");
        } catch (RuntimeException e) {
            assertEquals("Test throw", e.getMessage());
        }
    }

    // ========== Throwable behavior ==========

    @Test
    public void testStackTraceNotNull() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test");
        assertNotNull(ex.getStackTrace());
        assertTrue(ex.getStackTrace().length > 0);
    }

    @Test
    public void testToStringContainsMessage() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Specific error message");
        String str = ex.toString();
        assertTrue(str.contains("Specific error message"));
    }

    @Test
    public void testToStringContainsClassName() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException("Test");
        String str = ex.toString();
        assertTrue(str.contains("PjdbcCapabilitiesException"));
    }

    // ========== Real-world usage scenarios ==========

    @Test
    public void testManifestNotFoundScenario() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(
            "Capabilities manifest not found: pjdbc.capabilities.json"
        );
        assertTrue(ex.getMessage().contains("manifest not found"));
        assertTrue(ex.getMessage().contains("pjdbc.capabilities.json"));
    }

    @Test
    public void testParseErrorScenario() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(
            "Expected '{' at position 42 but found 'x'"
        );
        assertTrue(ex.getMessage().contains("position 42"));
    }

    @Test
    public void testIOErrorScenario() {
        IOException ioError = new IOException("Stream closed");
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(
            "Failed to load capabilities manifest", ioError
        );

        assertEquals("Failed to load capabilities manifest", ex.getMessage());
        assertTrue(ex.getCause() instanceof IOException);
        assertEquals("Stream closed", ex.getCause().getMessage());
    }

    @Test
    public void testUnterminatedStringScenario() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(
            "Unterminated string at position 156"
        );
        assertTrue(ex.getMessage().contains("Unterminated string"));
    }

    @Test
    public void testUnexpectedEndScenario() {
        PjdbcCapabilitiesException ex = new PjdbcCapabilitiesException(
            "Unexpected end of JSON"
        );
        assertTrue(ex.getMessage().contains("Unexpected end"));
    }
}
