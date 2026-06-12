// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds complete FIX messages embedded in arbitrary log lines.
 */
final class FixExtractor {
    private static final String BEGIN = "8=FIX";

    /** Extracts all complete messages, normalising a custom delimiter to SOH. */
    List<String> extractMessages(String line, char delimiter) {
        String normalised = delimiter == FixParser.SOH ? line : line.replace(delimiter, FixParser.SOH);
        List<String> messages = new ArrayList<>(2);
        int search = 0;
        while (search < normalised.length()) {
            int start = normalised.indexOf(BEGIN, search);
            // No BeginString means this log line has no decodable FIX payload.
            if (start < 0) {
                break;
            }
            int end = findMessageEnd(normalised, start);
            // A partial payload may be completed by a later follow-mode read, so stop here.
            if (end < 0) {
                break;
            }
            messages.add(normalised.substring(start, end));
            search = end;
        }
        return messages;
    }

    /** Locates the SOH after tag 10 when a complete checksum field is present. */
    private int findMessageEnd(String line, int start) {
        int index = start;
        while (index >= 0 && index < line.length()) {
            int checksum = line.indexOf(FixParser.SOH + "10=", index);
            if (checksum < 0) {
                checksum = line.indexOf("10=", index);
                // Without any checksum marker this candidate cannot be complete.
                if (checksum < 0) {
                    return -1;
                }
                // Bare 10= is accepted only when it starts the candidate or is SOH-delimited.
                if (checksum != start && checksum > 0 && line.charAt(checksum - 1) != FixParser.SOH) {
                    return -1;
                }
            } else {
                checksum++;
            }
            int valueStart = checksum + 3;
            int valueEnd = valueStart + 3;
            // FIX checksum must be exactly three digits followed by SOH.
            if (valueEnd < line.length()
                    && digits(line, valueStart, valueEnd)
                    && line.charAt(valueEnd) == FixParser.SOH) {
                return valueEnd + 1;
            }
            index = checksum + 1;
        }
        return -1;
    }

    /** Checks whether a slice contains only decimal digits. */
    private boolean digits(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char ch = value.charAt(index);
            // The checksum parser is intentionally strict: no signs or whitespace.
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }
}
