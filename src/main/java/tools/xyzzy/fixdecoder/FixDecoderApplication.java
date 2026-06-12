// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line entry point for the Java FIX decoder.
 */
@Command(
        name = "fixdecoder",
        mixinStandardHelpOptions = true,
        version = "fixdecoder 0.3.0 (java)",
        description = "Pretty-print FIX log messages and inspect FIX dictionaries.")
public final class FixDecoderApplication implements Callable<Integer> {
    @Option(names = "--xml", description = "Load a custom FIX XML dictionary.")
    private final List<Path> xmlFiles = new ArrayList<>();

    @Option(names = "--fix", defaultValue = "44", description = "Default FIX dictionary version.")
    private String fixVersion;

    @Option(names = "--info", description = "Show loaded dictionary summary.")
    private boolean info;

    @Option(names = "--message", arity = "0..1", fallbackValue = "", description = "Show message definition by name or MsgType.")
    private String message;

    @Option(names = "--component", arity = "0..1", fallbackValue = "", description = "Show component definition by name.")
    private String component;

    @Option(names = "--tag", arity = "0..1", fallbackValue = "-1", description = "Show tag definition by number.")
    private Integer tag;

    @Option(names = "--verbose", description = "Show enum details in dictionary modes.")
    private boolean verbose;

    @Option(names = "--column", description = "Compatibility flag for column output.")
    private boolean column;

    @Option(names = "--header", description = "Include message header in message display.")
    private boolean header;

    @Option(names = "--trailer", description = "Include message trailer in message display.")
    private boolean trailer;

    @Option(names = "--validate", description = "Validate messages while decoding.")
    private boolean validate;

    @Option(names = {"--colour", "--color"}, defaultValue = "auto", description = "Colour mode: yes, no, or auto.")
    private String colour;

    @Option(names = "--delimiter", defaultValue = "\u0001", description = "Input/output delimiter.")
    private String delimiter;

    @Option(names = "--secret", description = "Obfuscate sensitive values in decoded output.")
    private boolean secret;

    @Option(names = "--secret-files", description = "Write obfuscated .secret copies of input files.")
    private boolean secretFiles;

    @Option(names = "--secret-dir", description = "Directory for --secret-files output.")
    private Path secretDir;

    @Option(names = "--summary", description = "Show order summaries while decoding.")
    private boolean summary;

    @Option(names = "--follow", description = "Follow files as they grow.")
    private boolean follow;

    @Option(names = "--nocounts", description = "Suppress final message count summary.")
    private boolean noCounts;

    @Option(names = "--style", description = "Compatibility flag for bat-style decorations.")
    private String style;

    @Option(names = "--plain", description = "Compatibility flag for plain output.")
    private boolean plain;

    @Option(names = "--number", description = "Compatibility flag for line numbers.")
    private boolean number;

    @Option(names = "--paging", description = "Compatibility flag for pager mode.")
    private String paging;

    @Option(names = "--pager", description = "Compatibility flag for pager command.")
    private String pager;

    @Option(names = "--nowrap", description = "Compatibility flag for pager wrapping.")
    private boolean nowrap;

    @Parameters(arity = "0..*", description = "FIX log files, or stdin when omitted.")
    private final List<Path> files = new ArrayList<>();

    /** Launches the CLI with environment default arguments applied first. */
    public static void main(String[] args) {
        int exit = new CommandLine(new FixDecoderApplication()).execute(DefaultArguments.merge(args));
        System.exit(exit);
    }

    /** Routes dictionary inspection, secret-file generation, or streaming decode. */
    @Override
    public Integer call() throws Exception {
        DictionaryRegistry registry = new DictionaryRegistry();
        for (Path xml : xmlFiles) {
            registry.register(xml);
        }

        FixDictionary selected = registry.resolve(fixVersion);
        boolean colours = !"no".equalsIgnoreCase(colour);
        PrintWriter out = new PrintWriter(System.out, true);

        DictionaryDisplay display = new DictionaryDisplay(registry, colours);
        if (info) {
            display.printInfo(selected, out);
            return 0;
        }
        if (message != null) {
            display.printMessage(selected, emptyToNull(message), verbose, header, trailer, out);
            return 0;
        }
        if (component != null) {
            display.printComponent(selected, emptyToNull(component), verbose, out);
            return 0;
        }
        if (tag != null) {
            display.printTag(selected, tag < 0 ? null : tag, verbose, out);
            return 0;
        }

        char inputDelimiter = parseDelimiter(delimiter);
        FixFileProcessor processor = new FixFileProcessor(registry);
        if (secretFiles) {
            processor.writeSecretFiles(files, secretDir, inputDelimiter, out);
            return 0;
        }
        ProcessingOptions options = new ProcessingOptions(
                selected,
                inputDelimiter,
                validate,
                secret,
                noCounts,
                summary,
                follow,
                List.copyOf(files));
        processor.process(options, out);
        return 0;
    }

    /** Converts blank optional values from picocli into null. */
    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Parses SOH, hex, and literal delimiter values. */
    private char parseDelimiter(String value) {
        if (value == null || value.isEmpty() || "SOH".equalsIgnoreCase(value)) {
            return FixParser.SOH;
        }
        if (value.startsWith("\\x") && value.length() == 4) {
            return (char) Integer.parseInt(value.substring(2), 16);
        }
        return value.charAt(0);
    }
}
