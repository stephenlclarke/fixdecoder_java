// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small insertion-ordered generic registry used for dictionaries and lookup tables.
 *
 * @param <K> key type
 * @param <V> value type
 */
final class Registry<K, V> {
    private final Map<K, V> values = new LinkedHashMap<>();

    /** Stores or replaces a value. */
    void put(K key, V value) {
        values.put(key, value);
    }

    /** Returns a value or null when the key is unknown. */
    V get(K key) {
        return values.get(key);
    }

    /** Returns true when the key is registered. */
    boolean contains(K key) {
        return values.containsKey(key);
    }

    /** Returns insertion-ordered values. */
    Collection<V> values() {
        return values.values();
    }

    /** Returns insertion-ordered keys. */
    Collection<K> keys() {
        return values.keySet();
    }
}
