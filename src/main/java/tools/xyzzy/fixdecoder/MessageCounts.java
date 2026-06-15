// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks MsgType counts while preserving first-seen order.
 */
final class MessageCounts {
    private final Map<CountGroup, Map<String, Count>> counts = new EnumMap<>(CountGroup.class);

    /** Records one decoded message. */
    void add(FixMessage message, FixTagLookup lookup) {
        String msgType = message.valueOf(35);
        if (msgType == null) {
            msgType = "Unknown";
        }
        FixDictionary.MessageDef def = lookup.message(msgType);
        String label = def == null ? null : def.name();
        CountGroup group = CountGroup.from(def);
        counts.computeIfAbsent(group, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(msgType, ignored -> new Count(label))
                .increment();
    }

    /** Merges counts from a worker result. */
    void merge(MessageCounts other) {
        other.counts.forEach((group, groupCounts) -> groupCounts.forEach((msgType, count) -> counts
                .computeIfAbsent(group, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(msgType, ignored -> new Count(count.label))
                .add(count.value)));
    }

    /** Prints the summary table unless no messages were decoded. */
    void print(PrintWriter out) {
        if (counts.isEmpty()) {
            return;
        }
        out.println("----------------------------------------------");
        out.println("Message Counts:");
        for (CountGroup group : CountGroup.values()) {
            Map<String, Count> groupCounts = counts.get(group);
            if (groupCounts != null && !groupCounts.isEmpty()) {
                out.println(group.heading + ":");
                out.println("Message Type          Count  Name");
                groupCounts.forEach((msgType, count) ->
                        out.printf("%-18s %7d  %s%n", msgType, count.value, count.label == null ? "" : count.label));
                out.println();
            }
        }
        out.println();
    }

    /** Top-level message count buckets that mirror session/admin versus business traffic. */
    private enum CountGroup {
        SESSION_ADMIN("Session/Admin"),
        BUSINESS("Business"),
        UNKNOWN("Unknown");

        private final String heading;

        /** Creates a count bucket with its display heading. */
        CountGroup(String heading) {
            this.heading = heading;
        }

        /** Chooses a group from QuickFIX message metadata. */
        private static CountGroup from(FixDictionary.MessageDef def) {
            if (def == null) {
                return UNKNOWN;
            }
            if ("admin".equalsIgnoreCase(def.category())) {
                return SESSION_ADMIN;
            }
            return BUSINESS;
        }
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
