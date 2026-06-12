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
        FixExtractor extractor = new FixExtractor();
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
        List<String> fragments = new ArrayList<>();
        boolean changed = false;
        for (String fragment : message.split(String.valueOf(FixParser.SOH))) {
            if (fragment.isEmpty()) {
                continue;
            }
            int eq = fragment.indexOf('=');
            if (eq > 0) {
                int tag = parseTag(fragment, eq);
                String prefix = sensitive.get(tag);
                if (prefix != null) {
                    fragments.add(tag + "=" + alias(tag, fragment.substring(eq + 1), prefix));
                    changed = true;
                    continue;
                }
            }
            fragments.add(fragment);
        }
        if (!changed) {
            return message;
        }
        refreshLengths(fragments);
        return String.join(String.valueOf(FixParser.SOH), fragments) + FixParser.SOH;
    }

    /** Parses the tag number without creating a substring. */
    private int parseTag(String fragment, int end) {
        int tag = 0;
        for (int index = 0; index < end; index++) {
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
        String alias = prefix + String.format("%04d", counter);
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
        fragments.set(checksumIndex, "10=" + String.format("%03d", checksum % 256));
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

    /** Hash key for stable alias lookup. */
    /** Immutable alias-map key for a sensitive tag/value pair. */
    private record Key(int tag, String value) {
    }
}
