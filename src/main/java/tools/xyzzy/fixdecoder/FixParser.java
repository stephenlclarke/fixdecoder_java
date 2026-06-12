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
        int length = raw.length();
        int fragmentStart = 0;
        while (fragmentStart < length) {
            int valueEnd = raw.indexOf(SOH, fragmentStart);
            int fragmentEnd = valueEnd < 0 ? length : valueEnd;
            int equals = raw.indexOf('=', fragmentStart);
            int nextStart = nextFragmentStart(valueEnd, length);
            // Skip malformed fragments without '=' inside this SOH-delimited field.
            if (equals >= fragmentStart && equals < fragmentEnd) {
                int tag = parseTag(raw, fragmentStart, equals);
                if (tag >= 0) {
                    target.nextField().set(raw, tag, equals + 1, fragmentEnd);
                }
            }
            fragmentStart = nextStart;
        }
        return target;
    }

    /** Parses decimal tag digits in-place, returning -1 for malformed fragments. */
    private int parseTag(String raw, int start, int end) {
        if (start >= end) {
            return -1;
        }
        int tag = 0;
        for (int index = start; index < end; index++) {
            char ch = raw.charAt(index);
            // Any non-digit before '=' invalidates this fragment as a FIX tag.
            if (ch < '0' || ch > '9') {
                return -1;
            }
            tag = (tag * 10) + (ch - '0');
        }
        return tag;
    }

    /** Computes where the next SOH-delimited fragment starts. */
    private int nextFragmentStart(int valueEnd, int length) {
        return valueEnd < 0 ? length : valueEnd + 1;
    }
}
