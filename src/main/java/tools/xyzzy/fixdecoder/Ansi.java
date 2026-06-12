// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

/**
 * Centralises ANSI colour escape sequences used by dictionary display output.
 */
final class Ansi {
    static final String RESET = "\u001B[0m";
    static final String TAG = "\u001B[38;5;81m";
    static final String NAME = "\u001B[38;5;151m";
    static final String TYPE = "\u001B[38;5;228m";
    static final String ERROR = "\u001B[31m";

    private Ansi() {
    }

    /** Colours a FIX tag value when colour output is enabled. */
    static String tag(String value, boolean enabled) {
        return enabled ? TAG + value + RESET : value;
    }

    /** Colours a FIX field or component name when colour output is enabled. */
    static String name(String value, boolean enabled) {
        return enabled ? NAME + value + RESET : value;
    }

    /** Colours a FIX field type when colour output is enabled. */
    static String type(String value, boolean enabled) {
        return enabled ? TYPE + value + RESET : value;
    }

    /** Colours an error or required-field marker when colour output is enabled. */
    static String error(String value, boolean enabled) {
        return enabled ? ERROR + value + RESET : value;
    }
}
