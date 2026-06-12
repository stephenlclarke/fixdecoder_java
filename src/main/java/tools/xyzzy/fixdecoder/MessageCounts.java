// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks MsgType counts while preserving first-seen order.
 */
final class MessageCounts {
    private final Map<String, Count> counts = new LinkedHashMap<>();

    /** Records one decoded message. */
    void add(FixMessage message, FixTagLookup lookup) {
        String msgType = message.valueOf(35);
        if (msgType == null) {
            msgType = "Unknown";
        }
        FixDictionary.MessageDef def = lookup.message(msgType);
        String label = def == null ? null : def.name();
        counts.computeIfAbsent(msgType, ignored -> new Count(label)).increment();
    }

    /** Merges counts from a worker result. */
    void merge(MessageCounts other) {
        other.counts.forEach((msgType, count) -> counts
                .computeIfAbsent(msgType, ignored -> new Count(count.label))
                .add(count.value));
    }

    /** Prints the summary table unless no messages were decoded. */
    void print(PrintWriter out) {
        if (counts.isEmpty()) {
            return;
        }
        out.println("----------------------------------------------");
        out.println("Message Counts:");
        out.println("Message Type          Count  Name");
        counts.forEach((msgType, count) -> out.printf("%-18s %7d  %s%n", msgType, count.value, count.label == null ? "" : count.label));
        out.println();
    }

    /** Mutable count holder reused by the map. */
    private static final class Count {
        private final String label;
        private int value;

        private Count(String label) {
            this.label = label;
        }

        private void increment() {
            value++;
        }

        private void add(int delta) {
            value += delta;
        }
    }
}
