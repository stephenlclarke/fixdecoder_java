// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Streams stdin/files, extracts FIX messages, and coordinates concurrent file decoding.
 */
final class FixFileProcessor {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

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
    void process(ProcessingOptions options, PrintWriter out) throws Exception {
        if (options.files().isEmpty()) {
            MessageCounts counts = processReader(new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)), options, out, null);
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
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, workers));
        try {
            List<Future<ProcessingResult>> futures = new ArrayList<>();
            for (Path file : options.files()) {
                futures.add(executor.submit(new FileTask(file, options)));
            }
            MessageCounts merged = new MessageCounts();
            for (Future<ProcessingResult> future : futures) {
                ProcessingResult result = future.get();
                out.print(result.stdout());
                merged.merge(result.counts());
            }
            if (!options.noCounts()) {
                merged.print(out);
            }
        } finally {
            executor.shutdownNow();
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
            return processReader(new BufferedReader(new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)), options, out, null);
        }
        printFileHeader(path, out);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return processReader(reader, options, out, path);
        }
    }

    /** Streams a reader line-by-line using one reusable message buffer. */
    private MessageCounts processReader(
            BufferedReader reader,
            ProcessingOptions options,
            PrintWriter out,
            Path source) throws IOException {
        FixMessage reusable = new FixMessage();
        FixObfuscator obfuscator = new FixObfuscator(options.secret());
        MessageCounts counts = new MessageCounts();
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String displayLine = obfuscator.obfuscateLine(line, options.delimiter());
            List<String> messages = extractor.extractMessages(displayLine, options.delimiter());
            for (String raw : messages) {
                parser.parseInto(raw, reusable);
                FixDictionary dictionary = registry.resolveBeginString(reusable.valueOf(8), options.defaultDictionary());
                FixTagLookup lookup = new FixTagLookup(dictionary);
                ValidationReport report = options.validate() ? validator.validate(reusable, lookup) : null;
                if (options.validate() && report != null && !report.clean()) {
                    out.printf("Line %d: ", lineNumber);
                }
                prettifier.print(reusable, lookup, report, out);
                if (options.summary()) {
                    printSummary(reusable, out);
                }
                counts.add(reusable, lookup);
            }
            if (options.follow() && source != null) {
                out.flush();
            }
        }
        return counts;
    }

    /** Prints a compact order summary for the summary compatibility mode. */
    private void printSummary(FixMessage message, PrintWriter out) {
        String clOrdId = message.valueOf(11);
        String orderId = message.valueOf(37);
        String execType = message.valueOf(150);
        String ordStatus = message.valueOf(39);
        if (clOrdId == null && orderId == null && execType == null && ordStatus == null) {
            return;
        }
        out.println("Order Summary:");
        if (orderId != null) {
            out.println("    OrderID: " + orderId);
        }
        if (clOrdId != null) {
            out.println("    ClOrdID: " + clOrdId);
        }
        if (execType != null) {
            out.println("    ExecType: " + execType);
        }
        if (ordStatus != null) {
            out.println("    OrdStatus: " + ordStatus);
        }
        out.println();
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
        public ProcessingResult call() throws Exception {
            StringWriter buffer = new StringWriter(8192);
            PrintWriter writer = new PrintWriter(buffer);
            MessageCounts counts = processPath(file, options, writer);
            writer.flush();
            return new ProcessingResult(buffer.toString(), counts);
        }
    }
}
