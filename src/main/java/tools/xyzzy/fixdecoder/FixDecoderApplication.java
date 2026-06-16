// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Command-line entry point for the Java FIX decoder.
 */
@Command(
        name = "fixdecoder",
        mixinStandardHelpOptions = false,
        sortOptions = false,
        version = "fixdecoder 0.3.0 (java)",
        customSynopsis = {
                "fixdecoder [--xml=<xmlFiles>]... [--fix=<fixVersion>] [--info]",
                "           [--message[=<message>]] [--component[=<component>]]",
                "           [--tag[=<tag>]] [--column] [--verbose] [--header] [--trailer]",
                "           [--colour[=<colour>]] [--delimiter=<delimiter>] [--style=<style>]",
                "           [--plain] [--number] [--paging=<paging>] [--pager=<pager>]",
                "           [--nowrap] [--follow] [--validate] [--secret] [--secret-files]",
                "           [--summary] [--nocounts] [--secret-dir=<secretDir>]",
                "           [--help] [--version]",
                "           [<files>...]"
        },
        description = {
                "",
                "Pretty-print FIX log messages and inspect FIX dictionaries.",
                ""
        })
public final class FixDecoderApplication implements Callable<Integer> {
    private static final int ORDER_XML = 10;
    private static final int ORDER_FIX = 20;
    private static final int ORDER_INFO = 30;
    private static final int ORDER_MESSAGE = 40;
    private static final int ORDER_COMPONENT = 50;
    private static final int ORDER_TAG = 60;
    private static final int ORDER_COLUMN = 70;
    private static final int ORDER_VERBOSE = 80;
    private static final int ORDER_HEADER = 90;
    private static final int ORDER_TRAILER = 100;
    private static final int ORDER_COLOUR = 110;
    private static final int ORDER_DELIMITER = 120;
    private static final int ORDER_STYLE = 130;
    private static final int ORDER_PLAIN = 140;
    private static final int ORDER_NUMBER = 150;
    private static final int ORDER_PAGING = 160;
    private static final int ORDER_PAGER = 170;
    private static final int ORDER_NOWRAP = 180;
    private static final int ORDER_FOLLOW = 190;
    private static final int ORDER_VALIDATE = 200;
    private static final int ORDER_SECRET = 210;
    private static final int ORDER_SECRET_FILES = 220;
    private static final int ORDER_SUMMARY = 230;
    private static final int ORDER_NOCOUNTS = 240;
    private static final int ORDER_SECRET_DIR = 250;
    private static final int ORDER_HELP = 900;
    private static final int ORDER_VERSION = 910;
    private static final String USAGE_RESOURCE = "/messages/usage_en.txt";
    private static final String USAGE_TEXT = loadUsageText();

    @Spec
    private CommandSpec spec;

    @SuppressWarnings("java:S1068") // picocli reads this field reflectively.
    @Option(names = {"-h", "--help"}, order = ORDER_HELP, description = "Show this help message and exit.")
    private boolean help;

    @SuppressWarnings("java:S1068") // picocli reads this field reflectively.
    @Option(names = {"-v", "--version"}, versionHelp = true, order = ORDER_VERSION, description = "Print version information and exit.")
    private boolean version;

    @Option(names = "--xml", order = ORDER_XML, description = "Load a custom FIX XML dictionary.")
    private final List<Path> xmlFiles = new ArrayList<>();

    @Option(names = "--fix", order = ORDER_FIX, defaultValue = "44", description = "Default FIX dictionary version.")
    private String fixVersion;

    @Option(names = "--info", order = ORDER_INFO, description = "Show loaded dictionary summary.")
    private boolean info;

    @Option(names = "--message", order = ORDER_MESSAGE, arity = "0..1", fallbackValue = "", description = "Show message definition by name or MsgType.")
    private String message;

    @Option(names = "--component", order = ORDER_COMPONENT, arity = "0..1", fallbackValue = "", description = "Show component definition by name.")
    private String component;

    @Option(names = "--tag", order = ORDER_TAG, arity = "0..1", fallbackValue = "-1", description = "Show tag definition by number.")
    private Integer tag;

    @Option(names = "--verbose", order = ORDER_VERBOSE, description = "Show enum details in dictionary modes.")
    private boolean verbose;

    @Option(names = "--column", order = ORDER_COLUMN, description = "Compatibility flag for column output.")
    private boolean column;

    @Option(names = "--header", order = ORDER_HEADER, description = "Include message header in message display.")
    private boolean header;

    @Option(names = "--trailer", order = ORDER_TRAILER, description = "Include message trailer in message display.")
    private boolean trailer;

    @Option(names = "--validate", order = ORDER_VALIDATE, description = "Validate messages while decoding.")
    private boolean validate;

    @Option(
            names = "--colour",
            arity = "0..1",
            fallbackValue = "yes",
            defaultValue = "auto",
            order = ORDER_COLOUR,
            description = "Colour mode: yes, no, always, never, or auto.")
    private String colour;

    /** Accepts the US spelling alias without advertising it ahead of --colour. */
    @SuppressWarnings("java:S1144") // picocli invokes this method reflectively.
    @Option(names = "--color", hidden = true, arity = "0..1", fallbackValue = "yes")
    private void setColorAlias(String value) {
        this.colour = value;
    }

    @Option(names = "--delimiter", order = ORDER_DELIMITER, defaultValue = "\u0001", description = "Input/output delimiter.")
    private String delimiter;

    @Option(names = "--secret", order = ORDER_SECRET, description = "Obfuscate sensitive values in decoded output.")
    private boolean secret;

    @Option(names = "--secret-files", order = ORDER_SECRET_FILES, description = "Write obfuscated .secret copies of input files.")
    private boolean secretFiles;

    @Option(names = "--secret-dir", order = ORDER_SECRET_DIR, description = "Directory for --secret-files output.")
    private Path secretDir;

    @Option(names = "--summary", order = ORDER_SUMMARY, description = "Show order summaries while decoding.")
    private boolean summary;

    @Option(names = "--follow", order = ORDER_FOLLOW, description = "Follow files as they grow.")
    private boolean follow;

    @Option(names = "--nocounts", order = ORDER_NOCOUNTS, description = "Suppress final message count summary.")
    private boolean noCounts;

    @Option(names = "--style", order = ORDER_STYLE, description = "Compatibility flag for bat-style decorations.")
    private String style;

    @Option(names = "--plain", order = ORDER_PLAIN, description = "Compatibility flag for plain output.")
    private boolean plain;

    @Option(names = "--number", order = ORDER_NUMBER, description = "Compatibility flag for line numbers.")
    private boolean number;

    @Option(names = "--paging", order = ORDER_PAGING, description = "Compatibility flag for pager mode.")
    private String paging;

    @Option(names = "--pager", order = ORDER_PAGER, description = "Compatibility flag for pager command.")
    private String pager;

    @Option(names = "--nowrap", order = ORDER_NOWRAP, description = "Compatibility flag for pager wrapping.")
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
    public Integer call() throws IOException, InterruptedException, ExecutionException {
        PrintWriter out = spec.commandLine().getOut();
        if (help) {
            printUsage(out);
            return 0;
        }

        DictionaryRegistry registry = new DictionaryRegistry();
        for (Path xml : xmlFiles) {
            registry.register(xml);
        }

        FixDictionary selected = registry.resolve(fixVersion);
        boolean colours = coloursEnabled();

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

    private static String loadUsageText() {
        try (InputStream in = FixDecoderApplication.class.getResourceAsStream(USAGE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing usage resource: " + USAGE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load usage resource: " + USAGE_RESOURCE, ex);
        }
    }

    private static void printUsage(PrintWriter out) {
        out.print(USAGE_TEXT);
        if (!USAGE_TEXT.endsWith("\n")) {
            out.println();
        }
        out.flush();
    }

    /** Converts blank optional values from picocli into null. */
    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Resolves terminal-sensitive colour settings and validates user aliases. */
    private boolean coloursEnabled() {
        String mode = colour == null ? "auto" : colour.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "yes", "always", "true" -> true;
            case "no", "never", "false" -> false;
            case "auto" -> System.console() != null;
            default -> throw new CommandLine.ParameterException(
                    spec.commandLine(), "invalid value for --colour: " + colour);
        };
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
