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
}
