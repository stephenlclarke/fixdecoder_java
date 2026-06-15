// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Tests top-level CLI routing through picocli. */
class FixDecoderApplicationTest {
    /** Info mode should exit successfully and write the dictionary table to stdout. */
    @Test
    void routesInfoMode() {
        Captured captured = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--info", "--fix=44")));

        assertTrue(captured.stdout().contains("Loaded dictionaries:"));
    }

    /** Tag mode should exit successfully and write field metadata to stdout. */
    @Test
    void routesTagMode() {
        Captured captured = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--tag=35", "--fix=44")));

        assertTrue(captured.stdout().contains("MsgType"));
    }

    /** Auto colour should disable ANSI when the test process is not attached to a console. */
    @Test
    void autoColourSuppressesAnsiWhenNotTerminal() {
        Captured captured = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--message=D", "--fix=44")));

        assertFalse(captured.stdout().contains("\u001B["));
    }

    /** Bare --colour and --color=never aliases should force deterministic colour modes. */
    @Test
    void parsesColourAliases() {
        Captured forced = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--message=D", "--colour", "--fix=44")));
        Captured disabled = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--message=D", "--color=never", "--fix=44")));

        assertTrue(forced.stdout().contains("\u001B["));
        assertFalse(disabled.stdout().contains("\u001B["));
    }

    /** Invalid colour values should be rejected instead of silently enabling ANSI output. */
    @Test
    void rejectsInvalidColourMode() {
        CommandLine command = new CommandLine(new FixDecoderApplication());
        command.setErr(new PrintWriter(new StringWriter()));

        assertEquals(CommandLine.ExitCode.USAGE, command.execute("--info", "--colour=maybe"));
    }

    /** Help output should visually separate the one-line command description. */
    @Test
    void separatesDescriptionInHelpOutput() {
        Captured captured = capture(() -> assertEquals(0, new CommandLine(new FixDecoderApplication()).execute("--help")));
        String separator = System.lineSeparator();

        assertTrue(captured.stdout().contains(
                separator
                        + separator
                        + "Pretty-print FIX log messages and inspect FIX dictionaries."
                        + separator
                        + separator));
    }

    /** Captures stdout around a CLI invocation. */
    private Captured capture(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            runnable.run();
            return new Captured(stdout.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }

    /** Captured process streams. */
    private record Captured(String stdout) {
    }
}
