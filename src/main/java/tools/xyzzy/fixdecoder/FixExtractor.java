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
        return extract(line, delimiter).messages();
    }

    /** Extracts complete messages and returns any trailing incomplete FIX payload. */
    ExtractionResult extract(String line, char delimiter) {
        String normalised = delimiter == FixParser.SOH ? line : line.replace(delimiter, FixParser.SOH);
        List<String> messages = new ArrayList<>(2);
        int search = 0;
        int tailStart = -1;
        boolean scanning = true;
        while (scanning && search < normalised.length()) {
            int start = normalised.indexOf(BEGIN, search);
            if (start < 0) {
                // No BeginString means this log line has no decodable FIX payload.
                scanning = false;
            } else {
                ScanDecision decision = scanCandidate(normalised, start);
                if (decision.action() == ScanAction.MESSAGE) {
                    messages.add(normalised.substring(start, decision.position()));
                    search = decision.position();
                } else if (decision.action() == ScanAction.TAIL) {
                    tailStart = decision.position();
                    scanning = false;
                } else {
                    search = decision.position();
                }
            }
        }
        String tail = tailStart < 0 ? "" : normalised.substring(tailStart);
        return new ExtractionResult(messages, tail);
    }

    /** Classifies a candidate BeginString as complete, partial, or stale. */
    private ScanDecision scanCandidate(String line, int start) {
        int end = findMessageEnd(line, start);
        int nestedStart = nextMessageStart(line, start);
        if (end < 0 && nestedStart >= 0) {
            // A later BeginString before any checksum means this partial candidate is stale.
            return new ScanDecision(ScanAction.SKIP, nestedStart);
        }
        if (end < 0) {
            // A partial payload may be completed by a later follow-mode read, so stop here.
            return new ScanDecision(ScanAction.TAIL, start);
        }
        if (nestedStart >= 0 && nestedStart < end) {
            // Do not merge an abandoned partial payload with the next complete message.
            return new ScanDecision(ScanAction.SKIP, nestedStart);
        }
        return new ScanDecision(ScanAction.MESSAGE, end);
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

    /** Finds the next plausible BeginString after the current candidate. */
    private int nextMessageStart(String line, int currentStart) {
        int nextStart = line.indexOf(BEGIN, currentStart + BEGIN.length());
        while (nextStart >= 0 && !hasMessageBoundary(line, nextStart)) {
            nextStart = line.indexOf(BEGIN, nextStart + BEGIN.length());
        }
        return nextStart;
    }

    /** Accepts starts at line boundaries, log-token boundaries, or after SOH. */
    private boolean hasMessageBoundary(String line, int start) {
        return start == 0 || Character.isWhitespace(line.charAt(start - 1)) || line.charAt(start - 1) == FixParser.SOH;
    }

    /** Extraction output containing complete messages plus a retained partial tail. */
    record ExtractionResult(List<String> messages, String tail) {
    }

    /** Candidate scan action used to keep extraction control flow simple. */
    private enum ScanAction {
        MESSAGE,
        TAIL,
        SKIP
    }

    /** Candidate scan result; position is an end index, tail start, or next search point. */
    private record ScanDecision(ScanAction action, int position) {
    }
}
