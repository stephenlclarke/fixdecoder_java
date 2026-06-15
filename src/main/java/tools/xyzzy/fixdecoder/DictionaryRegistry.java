// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads built-in and custom FIX dictionaries and normalises user version input.
 */
final class DictionaryRegistry {
    private static final String FIX27 = "FIX27";
    private static final String FIX30 = "FIX30";
    private static final String FIX40 = "FIX40";

    private static final Map<String, String> APPL_VER_IDS = Map.ofEntries(
            Map.entry("0", FIX27),
            Map.entry("1", FIX30),
            Map.entry("2", FIX40),
            Map.entry("3", "FIX41"),
            Map.entry("4", "FIX42"),
            Map.entry("5", "FIX43"),
            Map.entry("6", "FIX44"),
            Map.entry("7", "FIX50"),
            Map.entry("8", "FIX50SP1"),
            Map.entry("9", "FIX50SP2"));

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
    private final Map<String, FixTagLookup> lookupCache = new ConcurrentHashMap<>();
    private final DictionaryXmlParser parser = new DictionaryXmlParser();

    /** Builds a registry with every embedded QuickFIX dictionary loaded. */
    DictionaryRegistry() {
        List<FixDictionary> loaded = new ArrayList<>();
        FixDictionary fix40 = null;
        for (String resource : BUILT_INS) {
            FixDictionary dictionary = parser.parseResource(resource, "built-in");
            loaded.add(dictionary);
            if (FIX40.equals(dictionary.key())) {
                fix40 = dictionary;
            }
        }
        if (fix40 == null) {
            throw new IllegalStateException("embedded FIX40 dictionary did not load");
        }
        dictionaries.put(FIX27, new DictionaryEntry(FIX27, fix40, "built-in alias of " + FIX40));
        dictionaries.put(FIX30, new DictionaryEntry(FIX30, fix40, "built-in alias of " + FIX40));
        for (FixDictionary dictionary : loaded) {
            dictionaries.put(dictionary.key(), new DictionaryEntry(dictionary.key(), dictionary, "built-in"));
        }
    }

    /** Registers a custom XML dictionary, replacing any existing entry for the same key. */
    void register(Path path) {
        FixDictionary dictionary = parser.parsePath(path);
        dictionaries.put(dictionary.key(), new DictionaryEntry(dictionary.key(), dictionary, path.toString()));
        lookupCache.remove(dictionary.key());
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
        String cleaned = beginString.trim();
        String key;
        if (cleaned.startsWith("FIXT.")) {
            key = "FIXT" + cleaned.substring("FIXT.".length()).replace(".", "");
        } else if (cleaned.startsWith("FIX.")) {
            key = "FIX" + cleaned.substring("FIX.".length()).replace(".", "");
        } else if (cleaned.startsWith("FIX")) {
            key = normaliseVersion(cleaned);
        } else {
            return fallback;
        }
        DictionaryEntry entry = dictionaries.get(key.toUpperCase(Locale.ROOT));
        return entry == null ? fallback : entry.dictionary();
    }

    /** Returns the FIX application dictionary named by ApplVerID, or the fallback if unknown. */
    FixDictionary resolveApplicationVersion(String applVerId, FixDictionary fallback) {
        if (applVerId == null || applVerId.isBlank()) {
            return fallback;
        }
        String cleaned = applVerId.trim().toUpperCase(Locale.ROOT);
        String key = APPL_VER_IDS.get(cleaned);
        if (key == null) {
            key = normaliseVersion(cleaned);
        }
        DictionaryEntry entry = dictionaries.get(key);
        return entry == null ? fallback : entry.dictionary();
    }

    /** Returns the shared lookup facade for a dictionary so hot paths do not rebuild maps. */
    FixTagLookup lookup(FixDictionary dictionary) {
        return lookupCache.computeIfAbsent(dictionary.key(), ignored -> new FixTagLookup(dictionary));
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

    /** Display metadata for a registered dictionary or alias. */
    record DictionaryEntry(String key, FixDictionary dictionary, String source) {
    }
}
