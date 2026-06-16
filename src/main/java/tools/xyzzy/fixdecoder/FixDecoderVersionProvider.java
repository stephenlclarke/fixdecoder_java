// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import picocli.CommandLine.IVersionProvider;

/** Supplies Rust/Go-shaped build metadata for the Java CLI. */
public final class FixDecoderVersionProvider implements IVersionProvider {
    private static final String DEFAULT_VERSION = "0.3.0";
    private static final String DEFAULT_BRANCH = "main";
    private static final String DEFAULT_COMMIT = "0000000";

    @Override
    public String[] getVersion() {
        return new String[] {versionString()};
    }

    static String versionString() {
        String version = version();
        if (dirty() && !version.endsWith("-dirty")) {
            version = version + "-dirty";
        }
        return String.format(
                "fixdecoder %s (branch:%s, commit:%s) [java:%s]",
                version,
                branch(),
                commit(),
                System.getProperty("java.version", "unknown"));
    }

    private static String version() {
        String version = firstNonBlank(
                System.getProperty("fixdecoder.version"),
                packageVersion(),
                DEFAULT_VERSION);
        return version.startsWith("v") ? version : "v" + version;
    }

    private static String branch() {
        return firstNonBlank(
                System.getProperty("fixdecoder.branch"),
                gitOutput("rev-parse", "--abbrev-ref", "HEAD").orElse(null),
                DEFAULT_BRANCH);
    }

    private static String commit() {
        return firstNonBlank(
                System.getProperty("fixdecoder.commit"),
                gitOutput("rev-parse", "--short", "HEAD").orElse(null),
                DEFAULT_COMMIT);
    }

    private static boolean dirty() {
        String configured = System.getProperty("fixdecoder.dirty");
        if (configured != null && !configured.isBlank()) {
            return Boolean.parseBoolean(configured);
        }
        return gitOutput("status", "--porcelain").isPresent();
    }

    private static String packageVersion() {
        Package packageInfo = FixDecoderVersionProvider.class.getPackage();
        return packageInfo == null ? null : packageInfo.getImplementationVersion();
    }

    private static Optional<String> gitOutput(String... args) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command(args));
            File gitDir = gitDirectory();
            if (gitDir != null) {
                builder.directory(gitDir);
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output;
            try (InputStream stdout = process.getInputStream()) {
                output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (process.waitFor() != 0 || output.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String[] command(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }

    private static File gitDirectory() {
        String configured = System.getProperty("fixdecoder.gitDir");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return new File(configured);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
