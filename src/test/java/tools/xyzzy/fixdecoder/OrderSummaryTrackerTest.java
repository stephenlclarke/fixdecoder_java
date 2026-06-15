// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** Tests bounded order-summary state used by long-running follow sessions. */
class OrderSummaryTrackerTest {
    /** Terminal order messages should be printed and then evicted from retained state. */
    @Test
    void terminalOrderIsEvictedAfterPrint() {
        DictionaryRegistry registry = new DictionaryRegistry();
        FixDictionary dictionary = registry.resolve("44");
        FixTagLookup lookup = registry.lookup(dictionary);
        FixMessage message = new FixParser().parseInto(
                "8=FIX.4.4\u00019=000\u000135=8\u000137=O1\u000111=C1\u0001150=2\u000139=2\u000110=000\u0001",
                new FixMessage());
        StringWriter buffer = new StringWriter();
        OrderSummaryTracker tracker = new OrderSummaryTracker();

        tracker.accept(message, lookup, new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("OrdStatus: 2"));
        assertEquals(0, tracker.trackedOrderCount());
    }
}
