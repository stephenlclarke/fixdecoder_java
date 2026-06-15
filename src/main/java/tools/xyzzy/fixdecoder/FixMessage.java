// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable parsed FIX message container owned by a processing worker.
 */
final class FixMessage {
    private final List<FixField> fields = new ArrayList<>(64);
    private final List<FixField> activeFields = Collections.unmodifiableList(new AbstractList<>() {
        @Override
        public FixField get(int index) {
            if (index < 0 || index >= fieldCount) {
                throw new IndexOutOfBoundsException(index);
            }
            return fields.get(index);
        }

        @Override
        public int size() {
            return fieldCount;
        }
    });
    private String raw = "";
    private int fieldCount;

    /** Clears previous fields and binds the container to a new raw message. */
    void reset(String raw) {
        this.raw = raw;
        fieldCount = 0;
    }

    /** Returns the next reusable field slot for the parser. */
    FixField nextField() {
        if (fieldCount == fields.size()) {
            // The pool grows only when a message exceeds the largest shape seen by this worker.
            fields.add(new FixField());
        }
        return fields.get(fieldCount++);
    }

    /** Returns the raw SOH-delimited message text. */
    String raw() {
        return raw;
    }

    /** Returns fields in wire order. */
    List<FixField> fields() {
        return activeFields;
    }

    /** Returns the first value for a tag, or null when absent. */
    String valueOf(int tag) {
        for (int index = 0; index < fieldCount; index++) {
            FixField field = fields.get(index);
            // FIX messages usually have unique header/body/trailer tags outside repeating groups.
            if (field.tag() == tag) {
                return field.value();
            }
        }
        return null;
    }

    /** Returns true when any field has the supplied tag. */
    boolean hasTag(int tag) {
        for (int index = 0; index < fieldCount; index++) {
            FixField field = fields.get(index);
            // Linear scan keeps the hot parse container compact and allocation-free.
            if (field.tag() == tag) {
                return true;
            }
        }
        return false;
    }
}
