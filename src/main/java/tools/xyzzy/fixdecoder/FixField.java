// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

/**
 * Mutable parsed FIX field that points into a message string to limit substring churn.
 */
final class FixField {
    private String source = "";
    private int tag;
    private int valueStart;
    private int valueEnd;

    /** Rebinds this reusable field object to a new tag/value slice. */
    void set(String source, int tag, int valueStart, int valueEnd) {
        this.source = source;
        this.tag = tag;
        this.valueStart = valueStart;
        this.valueEnd = valueEnd;
    }

    /** Returns the numeric FIX tag. */
    int tag() {
        return tag;
    }

    /** Materialises the field value only when a caller needs a String. */
    String value() {
        return source.substring(valueStart, valueEnd);
    }

    /** Compares a value without allocating a substring. */
    boolean valueEquals(String expected) {
        int length = valueEnd - valueStart;
        // A different length cannot match, so skip the character loop entirely.
        if (length != expected.length()) {
            return false;
        }
        for (int offset = 0; offset < length; offset++) {
            // Compare directly against the backing message to avoid temporary strings.
            if (source.charAt(valueStart + offset) != expected.charAt(offset)) {
                return false;
            }
        }
        return true;
    }

    /** Parses an integer value in place, returning a fallback on invalid content. */
    int parseIntValue(int fallback) {
        int value = 0;
        // Empty values are invalid for integer-like FIX fields.
        if (valueStart >= valueEnd) {
            return fallback;
        }
        for (int index = valueStart; index < valueEnd; index++) {
            char ch = source.charAt(index);
            // Non-digits make the value unusable for length/checksum validation.
            if (ch < '0' || ch > '9') {
                return fallback;
            }
            value = (value * 10) + (ch - '0');
        }
        return value;
    }
}
