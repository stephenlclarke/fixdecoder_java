// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads built-in and custom FIX dictionaries and normalises user version input.
 */
final class DictionaryRegistry {
    private static final List<String> BUILT_INS = List.of(
            "FIX40.xml",
            "FIX41.xml",
            "FIX42.xml",
            "FIX43.xml",
            "FIX44.xml",
            "FIX50.xml",
            "FIX50SP1.xml",
            "FIX50SP2.xml",
            "FIXT11.xml");

    private final Registry<String, DictionaryEntry> dictionaries = new Registry<>();
    private final DictionaryXmlParser parser = new DictionaryXmlParser();

    /** Builds a registry with every embedded QuickFIX dictionary loaded. */
    DictionaryRegistry() {
        List<FixDictionary> loaded = new ArrayList<>();
        FixDictionary fix40 = null;
        for (String resource : BUILT_INS) {
            FixDictionary dictionary = parser.parseResource(resource, "built-in");
            loaded.add(dictionary);
            if ("FIX40".equals(dictionary.key())) {
                fix40 = dictionary;
            }
        }
        if (fix40 == null) {
            throw new IllegalStateException("embedded FIX40 dictionary did not load");
        }
        dictionaries.put("FIX27", new DictionaryEntry("FIX27", fix40, "built-in alias of FIX40"));
        dictionaries.put("FIX30", new DictionaryEntry("FIX30", fix40, "built-in alias of FIX40"));
        for (FixDictionary dictionary : loaded) {
            dictionaries.put(dictionary.key(), new DictionaryEntry(dictionary.key(), dictionary, "built-in"));
        }
    }

    /** Registers a custom XML dictionary, replacing any existing entry for the same key. */
    void register(Path path) {
        FixDictionary dictionary = parser.parsePath(path);
        dictionaries.put(dictionary.key(), new DictionaryEntry(dictionary.key(), dictionary, path.toString()));
    }

    /** Resolves a user supplied version string to a loaded dictionary. */
    FixDictionary resolve(String requested) {
        String key = normaliseVersion(requested == null || requested.isBlank() ? "44" : requested);
        DictionaryEntry entry = dictionaries.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown FIX dictionary: " + requested);
        }
        return entry.dictionary();
    }

    /** Returns the dictionary matching a BeginString, or the fallback if unknown. */
    FixDictionary resolveBeginString(String beginString, FixDictionary fallback) {
        if (beginString == null || beginString.isBlank()) {
            return fallback;
        }
        String key;
        if (beginString.startsWith("FIXT.")) {
            key = "FIXT" + beginString.substring("FIXT.".length()).replace(".", "");
        } else {
            key = "FIX" + beginString.substring("FIX.".length()).replace(".", "");
        }
        DictionaryEntry entry = dictionaries.get(key.toUpperCase(Locale.ROOT));
        return entry == null ? fallback : entry.dictionary();
    }

    /** Returns dictionary entries in display order, including aliases. */
    List<DictionaryEntry> entries() {
        return new ArrayList<>(dictionaries.values());
    }

    /** Returns the canonical list used by the Rust implementation's info banner. */
    String availableKeys() {
        return String.join(",", dictionaries.keys());
    }

    /** Normalises values such as 44, 4.4, FIX44, FIX.4.4, and FIX50SP2. */
    static String normaliseVersion(String value) {
        String cleaned = value.trim().toUpperCase(Locale.ROOT).replace(".", "");
        if (cleaned.startsWith("FIXT")) {
            return cleaned;
        }
        if (!cleaned.startsWith("FIX")) {
            cleaned = "FIX" + cleaned;
        }
        return cleaned;
    }

    /** Metadata needed to display alias/custom dictionary source information. */
    /** Display metadata for a registered dictionary or alias. */
    record DictionaryEntry(String key, FixDictionary dictionary, String source) {
    }
}
