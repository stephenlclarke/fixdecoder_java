// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests line scanning for embedded FIX payloads. */
class FixExtractorTest {
    /** A log prefix and suffix should be ignored while the FIX payload is returned. */
    @Test
    void extractsEmbeddedMessage() {
        String line = "INFO 8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001 tail";

        assertEquals("8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001", new FixExtractor().extractMessages(line, FixParser.SOH).getFirst());
    }

    /** Custom display delimiters should be normalised to SOH internally. */
    @Test
    void extractsPipeDelimitedMessage() {
        String line = "8=FIX.4.4|9=005|35=0|10=000|";

        assertEquals("8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001", new FixExtractor().extractMessages(line, '|').getFirst());
    }

    /** Lines without a complete checksum field should not produce partial messages. */
    @Test
    void ignoresIncompleteMessages() {
        assertTrue(new FixExtractor().extractMessages("8=FIX.4.4\u00019=005\u000135=0\u0001", FixParser.SOH).isEmpty());
    }

    /** Partial messages should be retained so follow mode can complete them later. */
    @Test
    void retainsIncompleteTail() {
        FixExtractor.ExtractionResult result = new FixExtractor().extract("INFO 8=FIX.4.4\u00019=005\u000135=0\u0001", FixParser.SOH);

        assertTrue(result.messages().isEmpty());
        assertEquals("8=FIX.4.4\u00019=005\u000135=0\u0001", result.tail());
    }

    /** A stale partial before a fresh BeginString should not be merged into the next message. */
    @Test
    void skipsStalePartialBeforeNextMessage() {
        String valid = "8=FIX.4.4\u00019=005\u000135=A\u000110=000\u0001";
        String line = "8=FIX.4.4\u00019=005\u000135=0\u0001" + valid;

        FixExtractor.ExtractionResult result = new FixExtractor().extract(line, FixParser.SOH);

        assertEquals(1, result.messages().size());
        assertEquals(valid, result.messages().getFirst());
    }
}
