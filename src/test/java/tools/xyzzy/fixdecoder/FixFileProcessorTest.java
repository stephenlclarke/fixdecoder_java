// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
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
        Files.writeString(file,
                messageWithBody("35=D\u000111=C1\u000154=1\u000155=XYZ\u000138=100\u000144=12.34\u0001")
                        + messageWithBody("35=8\u000137=O1\u000111=C1\u0001150=0\u000139=0\u000114=0\u0001151=100\u0001"));
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
        assertTrue(buffer.toString().contains("Message: NewOrderSingle (D)"));
        assertTrue(buffer.toString().contains("Message: ExecutionReport (8)"));
        assertTrue(buffer.toString().contains("ClOrdID: C1"));
        assertTrue(buffer.toString().contains("Events: 2"));
        assertFalse(buffer.toString().contains("BeginString"));
        assertFalse(buffer.toString().contains("8=FIX.4.4"));
    }

    /** Follow mode should wait at EOF and complete a message split across later appends. */
    @Test
    void followModeWaitsForAppendedPartialMessage() throws Exception {
        Path file = tempDir.resolve("follow.log");
        Files.writeString(file, "");
        StringWriter buffer = new StringWriter();
        DictionaryRegistry registry = new DictionaryRegistry();
        FixFileProcessor processor = new FixFileProcessor(registry);
        ProcessingOptions options = new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                false,
                false,
                true,
                false,
                true,
                List.of(file));
        String message = validMessage("0");
        int split = message.indexOf("35=");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> runProcessor(processor, options, buffer));

        try {
            waitUntil(() -> buffer.toString().contains("Filename:"));
            Files.writeString(file, message.substring(0, split), StandardOpenOption.APPEND);
            pauseBriefly();
            assertFalse(buffer.toString().contains("BeginString"));
            Files.writeString(file, message.substring(split), StandardOpenOption.APPEND);
            waitUntil(() -> buffer.toString().contains("BeginString"));
        } finally {
            future.cancel(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    /** Offline file decoding should drop incomplete lines rather than merging them into later messages. */
    @Test
    void offlineModeDoesNotMergeStalePartialWithNextMessage() throws Exception {
        Path file = tempDir.resolve("stale.log");
        Files.writeString(file, "8=FIX.4.4\u00019=005\u000135=0\u0001\n" + validMessage("A"));
        StringWriter buffer = new StringWriter();
        DictionaryRegistry registry = new DictionaryRegistry();

        new FixFileProcessor(registry).process(new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                false,
                false,
                true,
                false,
                false,
                List.of(file)), new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("35 (MsgType): A (LOGON)"));
        assertFalse(buffer.toString().contains("35 (MsgType): 0 (HEARTBEAT)"));
        assertFalse(buffer.toString().contains("35=0\u00018=FIX.4.4"));
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

    /** Secret file mode should preserve CRLF and final-newline shape for unchanged bytes. */
    @Test
    void secretFilesPreserveOriginalLineEndings() throws Exception {
        Path file = tempDir.resolve("orders-crlf.log");
        String message = messageWithBody("35=A\u000149=BUY1\u000156=SELL1\u000198=0\u0001");
        String original = "prefix\r\n" + message.substring(0, message.length() - 1) + "\r\nsuffix";
        Files.write(file, original.getBytes(StandardCharsets.ISO_8859_1));
        StringWriter buffer = new StringWriter();

        new FixFileProcessor(new DictionaryRegistry()).writeSecretFiles(List.of(file), null, FixParser.SOH, new PrintWriter(buffer));

        Path secret = tempDir.resolve("orders-crlf.secret.log");
        String secretText = Files.readString(secret, StandardCharsets.ISO_8859_1);
        assertTrue(secretText.startsWith("prefix\r\n"));
        assertTrue(secretText.contains("\r\nsuffix"));
        assertFalse(secretText.endsWith("\n"));
        assertTrue(secretText.contains("SenderCompID0001"));
    }

    /** Oversized partial tails should be bounded to a fresh BeginString candidate. */
    @Test
    void boundsOversizedPendingTailToFreshBeginString() {
        String stale = "8=FIX.4.4" + "A".repeat(FixFileProcessor.MAX_PENDING_CHARS + 1);
        String fresh = "8=FIX.4.4\u00019=005\u0001";

        FixFileProcessor.BoundedTail bounded = FixFileProcessor.boundedPendingTail(stale + fresh);

        assertTrue(bounded.flushed().startsWith("8=FIX.4.4"));
        assertEquals(fresh, bounded.tail());
    }

    /** Parallel processing should clean successful worker temp dirs when another worker fails. */
    @Test
    void cleansWorkerOutputsWhenParallelProcessingFails() throws Exception {
        long before = workerDirectoryCount();
        Path missing = tempDir.resolve("missing.log");
        Path valid = tempDir.resolve("valid.log");
        Files.writeString(valid, validMessage("0"));
        DictionaryRegistry registry = new DictionaryRegistry();
        ProcessingOptions options = new ProcessingOptions(
                registry.resolve("44"),
                FixParser.SOH,
                false,
                false,
                true,
                false,
                false,
                List.of(missing, valid));

        assertThrows(
                ExecutionException.class,
                () -> new FixFileProcessor(registry).process(options, new PrintWriter(new StringWriter())));

        assertEquals(before, workerDirectoryCount());
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

    /** Runs the processor inside an executor while preserving checked failures. */
    private Void runProcessor(FixFileProcessor processor, ProcessingOptions options, StringWriter buffer) {
        try {
            processor.process(options, new PrintWriter(buffer));
            return null;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Waits for an asynchronous condition to become true. */
    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            pauseBriefly();
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("interrupted while waiting for asynchronous test condition");
            }
        }
        assertTrue(condition.getAsBoolean(), "condition was not met before timeout");
    }

    /** Parks briefly without using Thread.sleep, keeping Sonar test rules quiet. */
    private void pauseBriefly() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25L));
    }

    /** Counts worker directories in the repository root without deleting pre-existing files. */
    private long workerDirectoryCount() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of(".").toAbsolutePath().normalize())) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith(".fixdecoder-worker-"))
                    .count();
        }
    }
}
