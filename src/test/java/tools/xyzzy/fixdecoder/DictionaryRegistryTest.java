// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    /** Malformed BeginString values should fall back instead of crashing decode. */
    @Test
    void invalidBeginStringFallsBack() {
        DictionaryRegistry registry = new DictionaryRegistry();
        FixDictionary fallback = registry.resolve("44");

        assertSame(fallback, registry.resolveBeginString("FIX", fallback));
    }

    /** FIXT application messages should use the negotiated ApplVerID dictionary. */
    @Test
    void fixtSessionUsesNegotiatedApplicationDictionary() {
        DictionaryRegistry registry = new DictionaryRegistry();
        DictionarySession session = new DictionarySession(registry, registry.resolve("44"));
        FixParser parser = new FixParser();

        FixTagLookup logonLookup = session.lookupFor(parser.parseInto(
                "8=FIXT.1.1\u00019=000\u000135=A\u00011137=7\u000110=000\u0001", new FixMessage()));
        FixTagLookup orderLookup = session.lookupFor(parser.parseInto(
                "8=FIXT.1.1\u00019=000\u000135=D\u000111=ORDER-1\u000110=000\u0001", new FixMessage()));

        assertEquals("FIXT11", logonLookup.dictionary().key());
        assertEquals("FIX50", orderLookup.dictionary().key());
        assertEquals("NewOrderSingle", orderLookup.message("D").name());
    }

    /** Tag lookup facades should be cached per dictionary for hot decode paths. */
    @Test
    void lookupIsCachedPerDictionary() {
        DictionaryRegistry registry = new DictionaryRegistry();
        FixDictionary dictionary = registry.resolve("44");

        assertSame(registry.lookup(dictionary), registry.lookup(dictionary));
    }
}
