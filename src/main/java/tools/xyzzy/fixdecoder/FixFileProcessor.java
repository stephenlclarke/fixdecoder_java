// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Streams stdin/files, extracts FIX messages, and coordinates concurrent file decoding.
 */
final class FixFileProcessor {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final long FOLLOW_SLEEP_MILLIS = 100L;

    private final DictionaryRegistry registry;
    private final FixParser parser = new FixParser();
    private final FixExtractor extractor = new FixExtractor();
    private final FixValidator validator = new FixValidator();
    private final Prettifier prettifier = new Prettifier();

    /** Creates a processor backed by a dictionary registry. */
    FixFileProcessor(DictionaryRegistry registry) {
        this.registry = registry;
    }

    /** Processes stdin or paths according to the requested mode. */
    void process(ProcessingOptions options, PrintWriter out) throws IOException, InterruptedException, ExecutionException {
        if (options.files().isEmpty()) {
            MessageCounts counts = processReader(
                    new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)), options, out);
            if (!options.noCounts()) {
                counts.print(out);
            }
            out.flush();
            return;
        }

        if (options.files().size() == 1 || options.follow() || options.summary()) {
            MessageCounts counts = new MessageCounts();
            for (Path file : options.files()) {
                counts.merge(processPath(file, options, out));
            }
            if (!options.noCounts()) {
                counts.print(out);
            }
            out.flush();
            return;
        }

        int workers = Math.min(options.files().size(), Runtime.getRuntime().availableProcessors());
        try (ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, workers))) {
            List<Future<ProcessingResult>> futures = new ArrayList<>();
            for (Path file : options.files()) {
                futures.add(executor.submit(new FileTask(file, options)));
            }
            MessageCounts merged = new MessageCounts();
            for (Future<ProcessingResult> future : futures) {
                ProcessingResult result = future.get();
                try {
                    streamWorkerOutput(result.output(), out);
                    merged.merge(result.counts());
                } finally {
                    deleteWorkerOutput(result);
                }
            }
            if (!options.noCounts()) {
                merged.print(out);
            }
        }
        out.flush();
    }

    /** Generates secret sibling files without decoding output. */
    void writeSecretFiles(List<Path> files, Path secretDir, char delimiter, PrintWriter out) throws IOException {
        FixObfuscator obfuscator = new FixObfuscator(true);
        for (Path file : files) {
            obfuscator.reset();
            Path output = secretPath(file, secretDir);
            Files.createDirectories(output.toAbsolutePath().getParent());
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                    PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(obfuscator.obfuscateLine(line, delimiter));
                }
            }
            out.println(output);
        }
        out.flush();
    }

    /** Processes one path and returns counts; path "-" is treated as stdin. */
    private MessageCounts processPath(Path path, ProcessingOptions options, PrintWriter out) throws IOException {
        if ("-".equals(path.toString())) {
            return processReader(
                    new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)), options, out);
        }
        printFileHeader(path, out);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return processReader(reader, options, out);
        }
    }

    /** Streams a reader line-by-line using one reusable message buffer. */
    private MessageCounts processReader(BufferedReader reader, ProcessingOptions options, PrintWriter out)
            throws IOException {
        ProcessingContext context = new ProcessingContext(options, out);
        int lineNumber = 0;
        boolean reading = true;
        while (reading && !Thread.currentThread().isInterrupted()) {
            String line = reader.readLine();
            if (line == null) {
                // At EOF, follow mode pauses for more input while one-shot mode finishes.
                reading = options.follow() && waitForMoreInput(out);
            } else {
                lineNumber++;
                context.processLine(line, lineNumber);
                flushFollowOutput(options, out);
            }
        }
        return context.counts();
    }

    /** Flushes promptly in follow mode so tailed output appears immediately. */
    private void flushFollowOutput(ProcessingOptions options, PrintWriter out) {
        if (options.follow()) {
            out.flush();
        }
    }

    /** Sleeps briefly after EOF in follow mode and stops cleanly when interrupted. */
    private boolean waitForMoreInput(PrintWriter out) {
        out.flush();
        try {
            Thread.sleep(FOLLOW_SLEEP_MILLIS);
            return !Thread.currentThread().isInterrupted();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Deletes captured worker output and the private worker directory that contained it. */
    private void deleteWorkerOutput(ProcessingResult result) throws IOException {
        Files.deleteIfExists(result.output());
        Files.deleteIfExists(result.workingDirectory());
    }

    /** Streams captured worker output without materialising whole log output in memory. */
    private void streamWorkerOutput(Path output, PrintWriter out) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    /** Prints the bat-style file header used by file decode mode. */
    private void printFileHeader(Path path, PrintWriter out) throws IOException {
        String rule = "----------------------------------------------";
        Instant modified = Files.getLastModifiedTime(path).toInstant();
        out.println(rule);
        out.println("Filename: " + path);
        out.println("Last Modified: " + FILE_TIME.format(modified));
        out.println(rule);
        out.println();
    }

    /** Builds `name.secret.ext` paths, optionally inside a separate directory. */
    private Path secretPath(Path file, Path secretDir) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String secretName = dot > 0 ? name.substring(0, dot) + ".secret" + name.substring(dot) : name + ".secret";
        return secretDir == null ? file.resolveSibling(secretName) : secretDir.resolve(secretName);
    }

    /** Worker task used by parallel multi-file processing. */
    private final class FileTask implements Callable<ProcessingResult> {
        private final Path file;
        private final ProcessingOptions options;

        private FileTask(Path file, ProcessingOptions options) {
            this.file = file;
            this.options = options;
        }

        @Override
        public ProcessingResult call() throws IOException {
            Path workDir = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), ".fixdecoder-worker-");
            Path output = Files.createTempFile(workDir, "output-", ".out");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
                MessageCounts counts = processPath(file, options, writer);
                writer.flush();
                return new ProcessingResult(workDir, output, counts);
            } catch (IOException | RuntimeException ex) {
                Files.deleteIfExists(output);
                Files.deleteIfExists(workDir);
                throw ex;
            }
        }
    }

    /** Keeps per-reader mutable state together to minimise allocation on the decode path. */
    private final class ProcessingContext {
        private final ProcessingOptions options;
        private final PrintWriter out;
        private final FixMessage reusable = new FixMessage();
        private final FixObfuscator obfuscator;
        private final DictionarySession dictionarySession;
        private final OrderSummaryTracker summaryTracker = new OrderSummaryTracker();
        private final MessageCounts counts = new MessageCounts();
        private String pending = "";

        /** Creates a context with reusable decode collaborators for one input stream. */
        private ProcessingContext(ProcessingOptions options, PrintWriter out) {
            this.options = options;
            this.out = out;
            this.obfuscator = new FixObfuscator(options.secret());
            this.dictionarySession = new DictionarySession(registry, options.defaultDictionary());
        }

        /** Processes every complete FIX payload found on one input line. */
        private void processLine(String line, int lineNumber) {
            String displayLine = obfuscator.obfuscateLine(pending + line, options.delimiter());
            FixExtractor.ExtractionResult extracted = extractor.extract(displayLine, options.delimiter());
            List<String> messages = extracted.messages();
            for (String raw : messages) {
                processMessage(raw, lineNumber);
            }
            pending = options.follow() ? extracted.tail() : "";
        }

        /** Parses, validates, prints, and counts one raw FIX message. */
        private void processMessage(String raw, int lineNumber) {
            parser.parseInto(raw, reusable);
            FixTagLookup lookup = dictionarySession.lookupFor(reusable);
            ValidationReport report = validationReport(lookup);
            if (options.summary()) {
                // Summary mode intentionally suppresses full tag output.
                printSummaryMessage(lineNumber, lookup, report);
            } else {
                printDecodedMessage(lineNumber, lookup, report);
            }
            counts.add(reusable, lookup);
        }

        /** Prints compact summary output for one message. */
        private void printSummaryMessage(int lineNumber, FixTagLookup lookup, ValidationReport report) {
            printSummaryValidation(lineNumber, report);
            summaryTracker.accept(reusable, lookup, out);
        }

        /** Prints full decoded output for one message. */
        private void printDecodedMessage(int lineNumber, FixTagLookup lookup, ValidationReport report) {
            if (options.validate() && report != null && !report.clean()) {
                // Validation failures are prefixed with source line context.
                out.printf("Line %d: ", lineNumber);
            }
            prettifier.print(reusable, lookup, report, out);
        }

        /** Runs validation only when the caller requested it. */
        private ValidationReport validationReport(FixTagLookup lookup) {
            return options.validate() ? validator.validate(reusable, lookup) : null;
        }

        /** Prints validation failures in summary mode, where no full prettified message follows. */
        private void printSummaryValidation(int lineNumber, ValidationReport report) {
            if (options.validate() && report != null && !report.clean()) {
                out.printf("Line %d: %s%n", lineNumber, String.join("; ", report.errors()));
            }
        }

        /** Returns counts accumulated by this processing context. */
        private MessageCounts counts() {
            return counts;
        }
    }
}
