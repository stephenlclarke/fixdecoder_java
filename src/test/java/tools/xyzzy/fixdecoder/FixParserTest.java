// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests reusable FIX message parsing. */
class FixParserTest {
    /** A SOH-delimited message should parse into ordered tag/value fields. */
    @Test
    void parseExtractsOrderedFields() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());

        assertEquals(4, message.fields().size());
        assertEquals("FIX.4.4", message.valueOf(8));
        assertEquals("0", message.valueOf(35));
        assertTrue(message.fields().get(2).valueEquals("0"));
    }

    /** Parsing repeatedly into the same message should reuse field slots to limit GC churn. */
    @Test
    void parseReusesFieldSlots() {
        FixParser parser = new FixParser();
        FixMessage message = parser.parseInto("8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001", new FixMessage());
        FixField firstField = message.fields().getFirst();

        parser.parseInto("8=FIX.4.2\u00019=005\u000135=A\u000110=000\u0001", message);

        assertSame(firstField, message.fields().getFirst());
        assertEquals("FIX.4.2", message.valueOf(8));
    }
}
