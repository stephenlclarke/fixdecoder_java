// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable sensitive-tag obfuscator that recalculates BodyLength and CheckSum.
 */
final class FixObfuscator {
    private final boolean enabled;
    private final Map<Integer, String> sensitive;
    private final Map<Key, String> aliases = new HashMap<>();
    private final Map<Integer, Integer> counters = new HashMap<>();
    private final FixExtractor extractor = new FixExtractor();
    private final List<String> fragments = new ArrayList<>(64);

    /** Creates an obfuscator with process-local alias state. */
    FixObfuscator(boolean enabled) {
        this.enabled = enabled;
        this.sensitive = SensitiveTags.names();
    }

    /** Returns true when sensitive tags should be rewritten. */
    boolean enabled() {
        return enabled;
    }

    /** Clears alias state, normally before producing a new secret file. */
    void reset() {
        aliases.clear();
        counters.clear();
    }

    /** Obfuscates every FIX message found in a mixed log line. */
    String obfuscateLine(String line, char delimiter) {
        if (!enabled) {
            return line;
        }
        List<String> messages = extractor.extractMessages(line, delimiter);
        if (messages.isEmpty()) {
            return line;
        }
        String normalised = delimiter == FixParser.SOH ? line : line.replace(delimiter, FixParser.SOH);
        StringBuilder out = new StringBuilder(normalised.length());
        int cursor = 0;
        for (String message : messages) {
            int start = normalised.indexOf(message, cursor);
            out.append(normalised, cursor, start);
            out.append(obfuscateMessage(message));
            cursor = start + message.length();
        }
        out.append(normalised, cursor, normalised.length());
        return delimiter == FixParser.SOH ? out.toString() : out.toString().replace(FixParser.SOH, delimiter);
    }

    /** Rewrites sensitive fields inside one SOH-delimited message. */
    String obfuscateMessage(String message) {
        if (!enabled) {
            return message;
        }
        fragments.clear();
        boolean changed = false;
        int fragmentStart = 0;
        while (fragmentStart < message.length()) {
            int fragmentEnd = message.indexOf(FixParser.SOH, fragmentStart);
            int end = fragmentEnd < 0 ? message.length() : fragmentEnd;
            if (end > fragmentStart) {
                changed |= addObfuscatedFragment(message, fragmentStart, end);
            }
            fragmentStart = fragmentEnd < 0 ? message.length() : fragmentEnd + 1;
        }
        if (!changed) {
            return message;
        }
        refreshLengths(fragments);
        return joinFragments();
    }

    /** Adds one transformed fragment and reports whether its value changed. */
    private boolean addObfuscatedFragment(String message, int start, int end) {
        int eq = message.indexOf('=', start);
        if (eq <= start || eq >= end) {
            fragments.add(message.substring(start, end));
            return false;
        }
        int tag = parseTag(message, start, eq);
        String prefix = sensitive.get(tag);
        // Unknown or malformed tags are retained verbatim so non-FIX text is not damaged.
        if (prefix == null) {
            fragments.add(message.substring(start, end));
            return false;
        }
        fragments.add(tag + "=" + alias(tag, message.substring(eq + 1, end), prefix));
        return true;
    }

    /** Parses the tag number without creating a substring. */
    private int parseTag(String fragment, int start, int end) {
        int tag = 0;
        for (int index = start; index < end; index++) {
            char ch = fragment.charAt(index);
            if (ch < '0' || ch > '9') {
                return -1;
            }
            tag = (tag * 10) + (ch - '0');
        }
        return tag;
    }

    /** Returns a stable alias for one tag/value pair. */
    private String alias(int tag, String value, String prefix) {
        Key key = new Key(tag, value);
        String existing = aliases.get(key);
        if (existing != null) {
            return existing;
        }
        int counter = counters.merge(tag, 1, Integer::sum);
        String alias = prefix + fourDigits(counter);
        aliases.put(key, alias);
        return alias;
    }

    /** Recomputes FIX length fields after a sensitive value changes. */
    private void refreshLengths(List<String> fragments) {
        int bodyIndex = indexOfTag(fragments, "9");
        int checksumIndex = lastIndexOfTag(fragments, "10");
        if (bodyIndex < 0 || checksumIndex <= bodyIndex) {
            return;
        }
        int bodyLength = 0;
        for (int index = bodyIndex + 1; index < checksumIndex; index++) {
            bodyLength += fragments.get(index).length() + 1;
        }
        fragments.set(bodyIndex, "9=" + bodyLength);

        int checksum = 0;
        for (int index = 0; index < checksumIndex; index++) {
            String fragment = fragments.get(index);
            for (int offset = 0; offset < fragment.length(); offset++) {
                checksum += fragment.charAt(offset);
            }
            checksum += FixParser.SOH;
        }
        fragments.set(checksumIndex, "10=" + threeDigits(checksum % 256));
    }

    /** Joins rewritten fragments with SOH separators without regex helpers. */
    private String joinFragments() {
        StringBuilder out = new StringBuilder();
        for (String fragment : fragments) {
            out.append(fragment).append(FixParser.SOH);
        }
        return out.toString();
    }

    /** Formats a FIX checksum without the overhead of Formatter. */
    private String threeDigits(int value) {
        int normalized = Math.floorMod(value, 1000);
        return new String(new char[] {
                (char) ('0' + (normalized / 100)),
                (char) ('0' + ((normalized / 10) % 10)),
                (char) ('0' + (normalized % 10))
        });
    }

    /** Formats stable aliases with four decimal digits without Formatter allocation. */
    private String fourDigits(int value) {
        int normalized = Math.floorMod(value, 10_000);
        return new String(new char[] {
                (char) ('0' + (normalized / 1000)),
                (char) ('0' + ((normalized / 100) % 10)),
                (char) ('0' + ((normalized / 10) % 10)),
                (char) ('0' + (normalized % 10))
        });
    }

    /** Finds the first field with the requested tag. */
    private int indexOfTag(List<String> fragments, String tag) {
        for (int index = 0; index < fragments.size(); index++) {
            if (fragments.get(index).startsWith(tag + "=")) {
                return index;
            }
        }
        return -1;
    }

    /** Finds the last field with the requested tag. */
    private int lastIndexOfTag(List<String> fragments, String tag) {
        for (int index = fragments.size() - 1; index >= 0; index--) {
            if (fragments.get(index).startsWith(tag + "=")) {
                return index;
            }
        }
        return -1;
    }

    /** Immutable alias-map key for a sensitive tag/value pair. */
    private record Key(int tag, String value) {
    }

}
