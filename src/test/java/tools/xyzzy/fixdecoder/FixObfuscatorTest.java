// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Tests low-allocation sensitive field obfuscation. */
class FixObfuscatorTest {
    /** Obfuscated messages should keep stable aliases and correct FIX length fields. */
    @Test
    void obfuscatesAndRefreshesLengthFields() {
        String body = "35=A\u000149=BUY1\u000156=SELL1\u000198=0\u0001";
        String prefix = "8=FIX.4.4\u00019=" + body.length() + "\u0001" + body;
        String raw = prefix + "10=" + checksum(prefix) + "\u0001";

        String obfuscated = new FixObfuscator(true).obfuscateMessage(raw);
        Map<String, String> fields = Arrays.stream(obfuscated.substring(0, obfuscated.length() - 1).split("\u0001"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));

        assertEquals("SenderCompID0001", fields.get("49"));
        assertEquals("TargetCompID0001", fields.get("56"));
        assertTrue(lengthMatches(obfuscated));
        assertEquals(Integer.parseInt(fields.get("10")), Integer.parseInt(checksum(obfuscated.substring(0, obfuscated.lastIndexOf("\u000110=") + 1))));
    }

    /** Builds a three-digit FIX checksum. */
    private String checksum(String value) {
        int total = 0;
        for (int index = 0; index < value.length(); index++) {
            total += value.charAt(index);
        }
        return String.format("%03d", total % 256);
    }

    /** Verifies BodyLength against actual message content. */
    private boolean lengthMatches(String message) {
        Map<String, String> fields = Arrays.stream(message.substring(0, message.length() - 1).split("\u0001"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> right));
        int bodyStart = message.indexOf('\u0001', message.indexOf("9=")) + 1;
        int checksumStart = message.lastIndexOf("\u000110=");
        return Integer.parseInt(fields.get("9")) == checksumStart - bodyStart + 1;
    }
}
