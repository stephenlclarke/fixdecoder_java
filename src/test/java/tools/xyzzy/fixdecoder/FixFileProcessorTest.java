// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests file processing, counts, validation, and secret-file writing. */
class FixFileProcessorTest {
    @TempDir
    private Path tempDir;

    /** File processing should decode messages, print a header, and include message counts. */
    @Test
    void processesFileAndPrintsCounts() throws Exception {
        Path file = tempDir.resolve("orders.log");
        Files.writeString(file, validMessage("0"));
        StringWriter buffer = new StringWriter();
        DictionaryRegistry registry = new DictionaryRegistry();

        new FixFileProcessor(registry).process(new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                false,
                false,
                false,
                false,
                false,
                List.of(file)), new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("Filename: " + file));
        assertTrue(buffer.toString().contains("BeginString"));
        assertTrue(buffer.toString().contains("Message Counts:"));
    }

    /** Parallel processing should preserve argv output order. */
    @Test
    void processesMultipleFilesInArgumentOrder() throws Exception {
        Path first = tempDir.resolve("first.log");
        Path second = tempDir.resolve("second.log");
        Files.writeString(first, validMessage("0"));
        Files.writeString(second, validMessage("A"));
        StringWriter buffer = new StringWriter();
        DictionaryRegistry registry = new DictionaryRegistry();

        new FixFileProcessor(registry).process(new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                true,
                false,
                true,
                false,
                false,
                List.of(first, second)), new PrintWriter(buffer));

        assertTrue(buffer.toString().indexOf("Filename: " + first) < buffer.toString().indexOf("Filename: " + second));
        assertTrue(buffer.toString().contains("Line 1:"));
    }

    /** Summary mode should emit compact order state when order tags are present. */
    @Test
    void printsOrderSummaryWhenRequested() throws Exception {
        Path file = tempDir.resolve("summary.log");
        Files.writeString(file, messageWithBody("35=8\u000137=O1\u000111=C1\u0001150=0\u000139=0\u0001"));
        StringWriter buffer = new StringWriter();
        DictionaryRegistry registry = new DictionaryRegistry();

        new FixFileProcessor(registry).process(new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                false,
                false,
                true,
                true,
                false,
                List.of(file)), new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("Order Summary:"));
        assertTrue(buffer.toString().contains("ClOrdID: C1"));
    }

    /** Secret file mode should write a sibling file and leave input untouched. */
    @Test
    void writesSecretFiles() throws Exception {
        Path file = tempDir.resolve("orders.log");
        String original = "INFO " + messageWithBody("35=A\u000149=BUY1\u000156=SELL1\u000198=0\u0001") + " tail\n";
        Files.writeString(file, original);
        StringWriter buffer = new StringWriter();

        new FixFileProcessor(new DictionaryRegistry()).writeSecretFiles(List.of(file), null, FixParser.SOH, new PrintWriter(buffer));

        Path secret = tempDir.resolve("orders.secret.log");
        assertTrue(Files.exists(secret));
        assertEquals(original, Files.readString(file));
        assertTrue(Files.readString(secret).contains("SenderCompID0001"));
        assertTrue(buffer.toString().contains(secret.toString()));
    }

    /** Builds a valid message with a simple MsgType. */
    private String validMessage(String msgType) {
        return messageWithBody("35=" + msgType + "\u000149=S\u000156=T\u000134=1\u000152=20250101-00:00:00\u0001");
    }

    /** Builds a FIX 4.4 message with correct BodyLength and CheckSum. */
    private String messageWithBody(String body) {
        String prefix = "8=FIX.4.4\u00019=" + body.length() + "\u0001" + body;
        int checksum = 0;
        for (int index = 0; index < prefix.length(); index++) {
            checksum += prefix.charAt(index);
        }
        return prefix + "10=" + String.format("%03d", checksum % 256) + "\u0001\n";
    }
}
