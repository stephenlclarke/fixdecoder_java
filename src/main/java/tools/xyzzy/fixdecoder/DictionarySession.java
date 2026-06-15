// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

/**
 * Resolves the active dictionary for one decoded stream, including FIXT session negotiation.
 */
final class DictionarySession {
    private final DictionaryRegistry registry;
    private final FixDictionary fallback;
    private FixDictionary defaultApplicationDictionary;

    /** Creates a per-reader resolver with the command-line dictionary as fallback. */
    DictionarySession(DictionaryRegistry registry, FixDictionary fallback) {
        this.registry = registry;
        this.fallback = fallback;
    }

    /** Returns the cached tag lookup for the dictionary that applies to one message. */
    FixTagLookup lookupFor(FixMessage message) {
        rememberDefaultApplicationVersion(message);
        FixDictionary dictionary = dictionaryFor(message);
        return registry.lookup(dictionary);
    }

    /** Updates session state when a FIXT Logon declares DefaultApplVerID (1137). */
    private void rememberDefaultApplicationVersion(FixMessage message) {
        FixDictionary negotiated = registry.resolveApplicationVersion(message.valueOf(1137), null);
        if (negotiated != null) {
            defaultApplicationDictionary = negotiated;
        }
    }

    /** Resolves session dictionaries first, then FIXT application dictionaries when needed. */
    private FixDictionary dictionaryFor(FixMessage message) {
        String beginString = message.valueOf(8);
        FixDictionary sessionDictionary = registry.resolveBeginString(beginString, fallback);
        if (!fixt(beginString)) {
            return sessionDictionary;
        }
        String msgType = message.valueOf(35);
        if (sessionDictionary.message(msgType) != null) {
            return sessionDictionary;
        }
        FixDictionary explicit = registry.resolveApplicationVersion(message.valueOf(1128), null);
        if (explicit != null) {
            return explicit;
        }
        if (defaultApplicationDictionary != null) {
            return defaultApplicationDictionary;
        }
        return fallback;
    }

    /** Returns true only for FIXT BeginString values that need application-version negotiation. */
    private boolean fixt(String beginString) {
        return beginString != null && beginString.startsWith("FIXT.");
    }
}
