package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    /** Captures stdout around a CLI invocation. */
    private Captured capture(Runnable runnable) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
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
