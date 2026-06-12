// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable parsed FIX message container owned by a processing worker.
 */
final class FixMessage {
    private final List<FixField> fields = new ArrayList<>(64);
    private String raw = "";

    /** Clears previous fields and binds the container to a new raw message. */
    void reset(String raw) {
        this.raw = raw;
        fields.clear();
    }

    /** Allocates the next field slot for the parser. */
    FixField nextField() {
        FixField field = new FixField();
        fields.add(field);
        return field;
    }

    /** Returns the raw SOH-delimited message text. */
    String raw() {
        return raw;
    }

    /** Returns fields in wire order. */
    List<FixField> fields() {
        return Collections.unmodifiableList(fields);
    }

    /** Returns the first value for a tag, or null when absent. */
    String valueOf(int tag) {
        for (FixField field : fields) {
            // FIX messages usually have unique header/body/trailer tags outside repeating groups.
            if (field.tag() == tag) {
                return field.value();
            }
        }
        return null;
    }

    /** Returns true when any field has the supplied tag. */
    boolean hasTag(int tag) {
        for (FixField field : fields) {
            // Linear scan keeps the hot parse container compact and allocation-free.
            if (field.tag() == tag) {
                return true;
            }
        }
        return false;
    }
}
