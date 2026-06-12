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
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    out.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (escaping) {
            current.append('\\');
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }
}
