package org.pjdbc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PjdbcCli")
class PjdbcCliTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PjdbcCli cli;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        cli = new PjdbcCli(new PrintStream(outContent), new PrintStream(errContent));
    }

    private String getOutput() {
        return outContent.toString();
    }

    private String getError() {
        return errContent.toString();
    }

    @Nested
    @DisplayName("Help and Version")
    class HelpAndVersionTests {

        @Test
        @DisplayName("shows help with no arguments")
        void showsHelpWithNoArguments() {
            int result = cli.run(new String[]{});
            assertEquals(0, result);
            assertTrue(getOutput().contains("PJDBC CLI"));
            assertTrue(getOutput().contains("validate"));
            assertTrue(getOutput().contains("list"));
        }

        @Test
        @DisplayName("shows help with --help")
        void showsHelpWithFlag() {
            int result = cli.run(new String[]{"--help"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("PJDBC CLI"));
        }

        @Test
        @DisplayName("shows help with -h")
        void showsHelpWithShortFlag() {
            int result = cli.run(new String[]{"-h"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("PJDBC CLI"));
        }

        @Test
        @DisplayName("shows version with --version")
        void showsVersionWithFlag() {
            int result = cli.run(new String[]{"--version"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("PJDBC CLI version"));
        }

        @Test
        @DisplayName("shows version with -v")
        void showsVersionWithShortFlag() {
            int result = cli.run(new String[]{"-v"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("version"));
        }
    }

    @Nested
    @DisplayName("validate command")
    class ValidateTests {

        @Test
        @DisplayName("validates simple PJDBC URL")
        void validatesSimplePjdbcUrl() {
            int result = cli.run(new String[]{"validate", "jdbc:retry:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("URL structure is valid"));
            assertTrue(getOutput().contains("Subprotocol: retry"));
        }

        @Test
        @DisplayName("validates URL with parameters")
        void validatesUrlWithParameters() {
            int result = cli.run(new String[]{"validate", "jdbc:retry[maxRetries=5,initialDelay=200]:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("URL structure is valid"));
            assertTrue(getOutput().contains("maxRetries = 5"));
            assertTrue(getOutput().contains("initialDelay = 200"));
            assertTrue(getOutput().contains("All parameters valid"));
        }

        @Test
        @DisplayName("fails on invalid parameter value")
        void failsOnInvalidParameterValue() {
            int result = cli.run(new String[]{"validate", "jdbc:retry[maxRetries=-1]:jdbc:postgresql://localhost/db"});
            assertEquals(1, result);
            assertTrue(getOutput().contains("Parameter validation failed"));
        }

        @Test
        @DisplayName("fails on invalid parameter type")
        void failsOnInvalidParameterType() {
            int result = cli.run(new String[]{"validate", "jdbc:retry[maxRetries=abc]:jdbc:postgresql://localhost/db"});
            assertEquals(1, result);
            assertTrue(getOutput().contains("not a valid integer"));
        }

        @Test
        @DisplayName("requires URL argument")
        void requiresUrlArgument() {
            int result = cli.run(new String[]{"validate"});
            assertEquals(1, result);
            assertTrue(getError().contains("requires a URL argument"));
        }

        @Test
        @DisplayName("validates nested URLs recursively")
        void validatesNestedUrlsRecursively() {
            int result = cli.run(new String[]{"validate", "jdbc:readonly:jdbc:retry[maxRetries=3]:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Subprotocol: readonly"));
            assertTrue(getOutput().contains("Nested URL"));
            assertTrue(getOutput().contains("Subprotocol: retry"));
        }
    }

    @Nested
    @DisplayName("list command")
    class ListTests {

        @Test
        @DisplayName("lists all available drivers")
        void listsAllDrivers() {
            int result = cli.run(new String[]{"list"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Available PJDBC Drivers"));
            assertTrue(getOutput().contains("retry"));
            assertTrue(getOutput().contains("readonly"));
            assertTrue(getOutput().contains("timeout"));
        }

        @Test
        @DisplayName("shows driver count")
        void showsDriverCount() {
            int result = cli.run(new String[]{"list"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Total:"));
            assertTrue(getOutput().contains("drivers"));
        }
    }

    @Nested
    @DisplayName("show command")
    class ShowTests {

        @Test
        @DisplayName("shows driver details")
        void showsDriverDetails() {
            int result = cli.run(new String[]{"show", "retry"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Driver: retry"));
            assertTrue(getOutput().contains("RetryDriver"));
            assertTrue(getOutput().contains("Parameters:"));
            assertTrue(getOutput().contains("maxRetries"));
        }

        @Test
        @DisplayName("shows parameter constraints")
        void showsParameterConstraints() {
            int result = cli.run(new String[]{"show", "retry"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Type:"));
            assertTrue(getOutput().contains("Default:"));
            assertTrue(getOutput().contains("Min:"));
        }

        @Test
        @DisplayName("shows URL format")
        void showsUrlFormat() {
            int result = cli.run(new String[]{"show", "readonly"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("URL Format:"));
            assertTrue(getOutput().contains("jdbc:readonly"));
        }

        @Test
        @DisplayName("fails for unknown driver")
        void failsForUnknownDriver() {
            int result = cli.run(new String[]{"show", "unknown"});
            assertEquals(1, result);
            assertTrue(getError().contains("Driver not found"));
        }

        @Test
        @DisplayName("requires prefix argument")
        void requiresPrefixArgument() {
            int result = cli.run(new String[]{"show"});
            assertEquals(1, result);
            assertTrue(getError().contains("requires a driver prefix argument"));
        }
    }

    @Nested
    @DisplayName("chain command")
    class ChainTests {

        @Test
        @DisplayName("shows simple driver chain")
        void showsSimpleDriverChain() {
            int result = cli.run(new String[]{"chain", "jdbc:retry:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("Driver Chain Analysis"));
            assertTrue(getOutput().contains("RetryDriver"));
            assertTrue(getOutput().contains("2 layers"));
        }

        @Test
        @DisplayName("shows complex driver chain")
        void showsComplexDriverChain() {
            int result = cli.run(new String[]{"chain", "jdbc:readonly:jdbc:retry:jdbc:timeout:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("ReadonlyDriver"));
            assertTrue(getOutput().contains("RetryDriver"));
            assertTrue(getOutput().contains("TimeoutDriver"));
            assertTrue(getOutput().contains("4 layers"));
        }

        @Test
        @DisplayName("shows parameters in chain")
        void showsParametersInChain() {
            int result = cli.run(new String[]{"chain", "jdbc:timeout[queryTimeout=300]:jdbc:retry[maxRetries=5]:jdbc:postgresql://localhost/db"});
            assertEquals(0, result);
            assertTrue(getOutput().contains("queryTimeout=300"));
            assertTrue(getOutput().contains("maxRetries=5"));
        }

        @Test
        @DisplayName("requires URL argument")
        void requiresUrlArgument() {
            int result = cli.run(new String[]{"chain"});
            assertEquals(1, result);
            assertTrue(getError().contains("requires a URL argument"));
        }
    }

    @Nested
    @DisplayName("Unknown command")
    class UnknownCommandTests {

        @Test
        @DisplayName("fails for unknown command")
        void failsForUnknownCommand() {
            int result = cli.run(new String[]{"unknown"});
            assertEquals(1, result);
            assertTrue(getError().contains("Unknown command"));
        }
    }
}
