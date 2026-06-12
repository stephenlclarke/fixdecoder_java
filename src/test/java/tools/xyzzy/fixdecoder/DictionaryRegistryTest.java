// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** Tests built-in dictionary loading and normalisation. */
class DictionaryRegistryTest {
    /** FIX version shorthands should resolve to the same dictionary key. */
    @Test
    void normalisesFixVersions() {
        DictionaryRegistry registry = new DictionaryRegistry();

        assertEquals("FIX44", registry.resolve("44").key());
        assertEquals("FIX44", registry.resolve("FIX.4.4").key());
        assertEquals("FIX50SP2", registry.resolve("FIX50SP2").key());
    }

    /** The FIX 4.4 dictionary should expose standard fields and messages. */
    @Test
    void loadsFix44Definitions() {
        FixDictionary dictionary = new DictionaryRegistry().resolve("44");

        assertEquals("MsgType", dictionary.field(35).name());
        assertNotNull(dictionary.message("D"));
        assertEquals(106, dictionary.componentCount());
    }
}
