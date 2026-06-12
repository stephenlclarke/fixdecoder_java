package tools.xyzzy.fixdecoder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only facade over a dictionary for hot-path tag and enum lookups.
 */
final class FixTagLookup {
    private final FixDictionary dictionary;
    private final Map<Integer, String> names = new LinkedHashMap<>();
    private final Map<Integer, String> types = new LinkedHashMap<>();
    private final Map<Integer, Map<String, String>> enums = new LinkedHashMap<>();

    /** Precomputes maps from immutable dictionary metadata. */
    FixTagLookup(FixDictionary dictionary) {
        this.dictionary = dictionary;
        for (FixDictionary.FieldDef field : dictionary.fields()) {
            names.put(field.number(), field.name());
            types.put(field.number(), field.type());
            enums.put(field.number(), field.enums());
        }
    }

    /** Returns the human-readable field name, falling back to the numeric tag. */
    String fieldName(int tag) {
        return names.getOrDefault(tag, Integer.toString(tag));
    }

    /** Returns the FIX field type when known. */
    String fieldType(int tag) {
        return types.get(tag);
    }

    /** Returns the enum description for a value when the dictionary declares one. */
    String enumDescription(int tag, String value) {
        Map<String, String> values = enums.get(tag);
        return values == null ? null : values.get(value);
    }

    /** Returns true when the tag exists in the active dictionary. */
    boolean hasTag(int tag) {
        return names.containsKey(tag);
    }

    /** Resolves a message definition by MsgType. */
    FixDictionary.MessageDef message(String msgType) {
        return dictionary.message(msgType);
    }

    /** Exposes the source dictionary for display modes. */
    FixDictionary dictionary() {
        return dictionary;
    }
}
