// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

/**
 * Allocation-conscious parser for SOH-delimited FIX messages.
 */
final class FixParser {
    static final char SOH = '\u0001';

    /** Parses raw text into the supplied reusable message container. */
    FixMessage parseInto(String raw, FixMessage target) {
        target.reset(raw);
        int index = 0;
        int length = raw.length();
        while (index < length) {
            int tagStart = index;
            int tag = 0;
            boolean tagValid = false;
            while (index < length) {
                char ch = raw.charAt(index);
                // An equals sign terminates a candidate tag number.
                if (ch == '=') {
                    tagValid = index > tagStart;
                    index++;
                    break;
                }
                // Any non-digit before '=' invalidates this fragment as a FIX tag.
                if (ch < '0' || ch > '9') {
                    tagValid = false;
                } else if (tagValid || index == tagStart) {
                    tag = (tag * 10) + (ch - '0');
                    tagValid = true;
                }
                index++;
            }
            int valueStart = index;
            while (index < length && raw.charAt(index) != SOH) {
                index++;
            }
            int valueEnd = index;
            // Skip malformed fragments without '=' or without a numeric tag.
            if (tagValid && valueEnd >= valueStart) {
                target.nextField().set(raw, tag, valueStart, valueEnd);
            }
            // Advance over SOH so the next loop starts at the next fragment.
            if (index < length && raw.charAt(index) == SOH) {
                index++;
            }
        }
        return target;
    }
}
