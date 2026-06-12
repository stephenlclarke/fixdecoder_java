package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation result with both message-level and tag-level errors.
 */
final class ValidationReport {
    private final List<String> errors = new ArrayList<>();
    private final Map<Integer, List<String>> tagErrors = new LinkedHashMap<>();

    /** Adds an error and attaches it to a tag for inline rendering. */
    void add(int tag, String error) {
        errors.add(error);
        tagErrors.computeIfAbsent(tag, ignored -> new ArrayList<>()).add(error);
    }

    /** Adds a message-level error when there is no useful tag anchor. */
    void add(String error) {
        errors.add(error);
    }

    /** Returns true when validation found no protocol issue. */
    boolean clean() {
        return errors.isEmpty();
    }

    /** Returns all validation errors in detection order. */
    List<String> errors() {
        return errors;
    }

    /** Returns tag-specific errors used by the prettifier. */
    Map<Integer, List<String>> tagErrors() {
        return tagErrors;
    }
}
