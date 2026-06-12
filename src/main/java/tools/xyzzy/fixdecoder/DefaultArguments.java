// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits FIXDECODER_DEFAULT_ARGS using the shell-like quoting expected by the Rust CLI.
 */
final class DefaultArguments {
    static final String ENV = "FIXDECODER_DEFAULT_ARGS";

    private DefaultArguments() {
    }

    /** Prepends environment defaults before real command-line arguments. */
    static String[] merge(String[] args) {
        String defaults = System.getenv(ENV);
        if (defaults == null || defaults.isBlank()) {
            return args;
        }
        List<String> merged = new ArrayList<>(split(defaults));
        merged.addAll(List.of(args));
        return merged.toArray(String[]::new);
    }

    /** Splits a small shell-style argument string with single and double quotes. */
    static List<String> split(String value) {
        ArgumentSplitState state = new ArgumentSplitState();
        for (int index = 0; index < value.length(); index++) {
            // The state object owns quoting rules so this loop stays purely sequential.
            state.accept(value.charAt(index));
        }
        return state.finish();
    }

    /** Stateful splitter for shell-like quotes and backslash escaping. */
    private static final class ArgumentSplitState {
        private final List<String> arguments = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();
        private char quote;
        private boolean escaping;

        /** Accepts one character from the defaults string. */
        private void accept(char ch) {
            if (escaping) {
                appendEscaped(ch);
            } else if (ch == '\\') {
                escaping = true;
            } else if (quoted()) {
                acceptQuoted(ch);
            } else {
                acceptUnquoted(ch);
            }
        }

        /** Returns parsed arguments after preserving a trailing literal backslash. */
        private List<String> finish() {
            if (escaping) {
                current.append('\\');
            }
            appendCurrentArgument();
            return arguments;
        }

        /** Appends a character that was escaped by a preceding backslash. */
        private void appendEscaped(char ch) {
            current.append(ch);
            escaping = false;
        }

        /** Handles characters while inside either quote type. */
        private void acceptQuoted(char ch) {
            if (ch == quote) {
                quote = 0;
            } else {
                current.append(ch);
            }
        }

        /** Handles characters while not inside a quoted string. */
        private void acceptUnquoted(char ch) {
            if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                appendCurrentArgument();
            } else {
                current.append(ch);
            }
        }

        /** Returns true when a single or double quote is currently open. */
        private boolean quoted() {
            return quote != 0;
        }

        /** Emits the current token if it contains any characters. */
        private void appendCurrentArgument() {
            if (!current.isEmpty()) {
                arguments.add(current.toString());
                current.setLength(0);
            }
        }
    }
}
