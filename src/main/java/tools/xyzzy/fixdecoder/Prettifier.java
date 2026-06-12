package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts parsed FIX messages into the Rust-compatible prettified text layout.
 */
final class Prettifier {
    /** Writes the raw message and one decoded field line per tag. */
    void print(FixMessage message, FixTagLookup lookup, ValidationReport report, PrintWriter out) {
        out.println(message.raw());
        out.println();
        Set<Integer> seen = new HashSet<>();
        Map<Integer, List<String>> tagErrors = report == null ? Map.of() : report.tagErrors();
        for (FixField field : message.fields()) {
            seen.add(field.tag());
            String value = field.value();
            String enumDescription = lookup.enumDescription(field.tag(), value);
            out.printf(
                    "%6d (%s): %s",
                    field.tag(),
                    lookup.fieldName(field.tag()),
                    value);
            if (enumDescription != null && !enumDescription.isBlank()) {
                out.printf(" (%s)", enumDescription);
            }
            List<String> errors = tagErrors.get(field.tag());
            if (errors != null && !errors.isEmpty()) {
                out.print("  " + String.join("; ", errors));
            }
            out.println();
        }
        if (report != null) {
            for (Map.Entry<Integer, List<String>> entry : tagErrors.entrySet()) {
                if (!seen.contains(entry.getKey())) {
                    out.printf("%6d (%s): %s%n", entry.getKey(), lookup.fieldName(entry.getKey()), String.join("; ", entry.getValue()));
                }
            }
        }
        out.println();
    }
}
