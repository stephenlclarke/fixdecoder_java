// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests validation errors that are surfaced inline by the prettifier. */
class FixValidatorTest {
    /** Missing required header fields and a bad checksum should be reported. */
    @Test
    void reportsMissingFieldsAndChecksumMismatch() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        ValidationReport report = new FixValidator().validate(message, new FixTagLookup(new DictionaryRegistry().resolve("44")));

        assertFalse(report.clean());
        assertTrue(report.tagErrors().get(10).getFirst().contains("Checksum mismatch"));
        assertTrue(report.tagErrors().containsKey(49));
        assertTrue(report.tagErrors().containsKey(56));
    }

    /** Timestamp validation should reject junk after an otherwise valid time. */
    @Test
    void rejectsTimestampTrailingJunk() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000149=S\u000156=T\u000134=1\u000152=20250101-00:00:00BAD\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        ValidationReport report = new FixValidator().validate(message, new FixTagLookup(new DictionaryRegistry().resolve("44")));

        assertFalse(report.clean());
        assertTrue(report.tagErrors().get(52).getFirst().contains("Invalid UTCTIMESTAMP"));
    }

    /** Missing MsgType should be reported cleanly instead of crashing validation. */
    @Test
    void reportsMissingMsgType() {
        String raw = "8=FIX.4.4\u00019=005\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        ValidationReport report = new FixValidator().validate(message, new FixTagLookup(new DictionaryRegistry().resolve("44")));

        assertFalse(report.clean());
        assertTrue(report.tagErrors().get(35).getFirst().contains("Missing required tag 35"));
    }

    /** Repeated tags inside FIX repeating groups should not be reported as duplicate fields. */
    @Test
    void acceptsRepeatingGroupMemberDuplicates() {
        String raw = "8=FIX.4.4\u00019=199\u000135=D\u000149=BUY1\u000156=SELL1\u000134=1\u000152=20260424-10:00:00.000"
                + "\u000111=ORD-1001\u0001453=2\u0001448=DEUTDEFF\u0001447=B\u0001452=1\u0001802=1\u0001523=ACC-12345"
                + "\u0001803=10\u0001448=CLIENT01\u0001447=D\u0001452=5\u000155=IBM\u000154=1"
                + "\u000160=20260424-10:00:00.000\u000140=2\u000144=185.25\u000110=080\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        ValidationReport report = new FixValidator().validate(message, new FixTagLookup(new DictionaryRegistry().resolve("44")));

        assertFalse(report.errors().stream().anyMatch(error -> error.contains("Duplicate tag 448")));
        assertFalse(report.errors().stream().anyMatch(error -> error.contains("Duplicate tag 447")));
        assertFalse(report.errors().stream().anyMatch(error -> error.contains("Duplicate tag 452")));
    }

    /** Duplicate non-group tags should still be reported as validation errors. */
    @Test
    void rejectsTopLevelDuplicateTags() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000149=S\u000149=S2\u000156=T\u000134=1\u000152=20250101-00:00:00\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        ValidationReport report = new FixValidator().validate(message, new FixTagLookup(new DictionaryRegistry().resolve("44")));

        assertTrue(report.tagErrors().get(49).stream().anyMatch(error -> error.contains("Duplicate tag 49")));
    }
}
