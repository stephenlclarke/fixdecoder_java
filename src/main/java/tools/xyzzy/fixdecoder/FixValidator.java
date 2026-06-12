// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stateless protocol validator for one parsed FIX message.
 */
final class FixValidator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /** Validates required fields, field existence, simple types, BodyLength, and CheckSum. */
    ValidationReport validate(FixMessage message, FixTagLookup lookup) {
        ValidationReport report = new ValidationReport();
        Map<Integer, FixField> lastByTag = new HashMap<>();
        Set<Integer> seen = new HashSet<>();
        boolean duplicateDetected = false;

        for (FixField field : message.fields()) {
            if (!lookup.hasTag(field.tag())) {
                report.add(field.tag(), "Unknown tag " + field.tag() + " in " + lookup.dictionary().key());
            }
            if (!seen.add(field.tag())) {
                duplicateDetected = true;
                report.add(field.tag(), "Duplicate tag " + field.tag() + " encountered");
            }
            lastByTag.put(field.tag(), field);
            validateType(field, lookup, report);
        }

        FixDictionary.MessageDef def = lookup.message(value(lastByTag, 35));
        if (def == null) {
            report.add(35, value(lastByTag, 35) == null ? "Missing required tag 35 (MsgType)" : "Unknown MsgType: " + value(lastByTag, 35));
        } else {
            for (int required : def.requiredTags()) {
                if (!message.hasTag(required)) {
                    report.add(required, "Missing required tag " + required + " (" + lookup.fieldName(required) + ")");
                }
            }
            if (!duplicateDetected) {
                validateOrdering(message, def, report);
            }
        }

        validateBodyLength(message.raw(), lastByTag, report);
        validateChecksum(message.raw(), lastByTag, report);
        return report;
    }

    /** Returns the most recently seen value for a tag. */
    private String value(Map<Integer, FixField> fields, int tag) {
        FixField field = fields.get(tag);
        return field == null ? null : field.value();
    }

    /** Checks simple dictionary types without allocating parser helpers on the hot path. */
    private void validateType(FixField field, FixTagLookup lookup, ValidationReport report) {
        String type = lookup.fieldType(field.tag());
        if (type == null) {
            return;
        }
        String value = field.value();
        String enumDescription = lookup.enumDescription(field.tag(), value);
        if (lookup.dictionary().field(field.tag()) != null
                && !lookup.dictionary().field(field.tag()).enums().isEmpty()
                && enumDescription == null) {
            report.add(field.tag(), "Invalid enum value " + value + " for tag " + field.tag());
        }
        switch (type) {
            case "INT", "SEQNUM", "NUMINGROUP", "LENGTH" -> validateInteger(field, report);
            case "BOOLEAN" -> {
                if (!"Y".equals(value) && !"N".equals(value)) {
                    report.add(field.tag(), "Invalid BOOLEAN value " + value + " for tag " + field.tag());
                }
            }
            case "UTCTIMESTAMP" -> validateTimestamp(field, report);
            case "LOCALMKTDATE", "UTCDATEONLY" -> validateDate(field, report);
            default -> {
            }
        }
    }

    /** Validates integer-like fields. */
    private void validateInteger(FixField field, ValidationReport report) {
        String value = field.value();
        if (value.isBlank()) {
            report.add(field.tag(), "Invalid numeric value for tag " + field.tag());
            return;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch < '0' || ch > '9') {
                report.add(field.tag(), "Invalid numeric value " + value + " for tag " + field.tag());
                return;
            }
        }
    }

    /** Validates FIX UTC timestamp formats commonly used by QuickFIX specs. */
    private void validateTimestamp(FixField field, ValidationReport report) {
        String value = field.value();
        int dash = value.indexOf('-');
        if (dash != 8) {
            report.add(field.tag(), "Invalid UTCTIMESTAMP value " + value + " for tag " + field.tag());
            return;
        }
        try {
            LocalDate.parse(value.substring(0, 8), DATE);
            String time = value.substring(9);
            if (time.length() >= 8) {
                LocalTime.parse(time.substring(0, 8));
            } else {
                report.add(field.tag(), "Invalid UTCTIMESTAMP value " + value + " for tag " + field.tag());
            }
        } catch (DateTimeException ex) {
            report.add(field.tag(), "Invalid UTCTIMESTAMP value " + value + " for tag " + field.tag());
        }
    }

    /** Validates YYYYMMDD date fields. */
    private void validateDate(FixField field, ValidationReport report) {
        try {
            LocalDate.parse(field.value(), DATE);
        } catch (DateTimeParseException ex) {
            report.add(field.tag(), "Invalid date value " + field.value() + " for tag " + field.tag());
        }
    }

    /** Validates approximate field ordering against dictionary order. */
    private void validateOrdering(FixMessage message, FixDictionary.MessageDef def, ValidationReport report) {
        int lastOrder = -1;
        for (FixField field : message.fields()) {
            int order = def.fieldOrder().indexOf(field.tag());
            if (order >= 0) {
                if (order < lastOrder) {
                    report.add(field.tag(), "Tag " + field.tag() + " appears out of dictionary order");
                }
                lastOrder = Math.max(lastOrder, order);
            }
        }
    }

    /** Validates tag 9 by measuring bytes between the body and checksum fields. */
    private void validateBodyLength(String raw, Map<Integer, FixField> fields, ValidationReport report) {
        FixField bodyLength = fields.get(9);
        if (bodyLength == null) {
            report.add(9, "Missing required tag 9 (BodyLength)");
            return;
        }
        int bodyStart = raw.indexOf(FixParser.SOH, raw.indexOf("9="));
        int checksumMarker = raw.lastIndexOf(FixParser.SOH + "10=");
        if (bodyStart < 0 || checksumMarker < 0) {
            return;
        }
        int actual = checksumMarker - bodyStart;
        int declared = bodyLength.parseIntValue(-1);
        if (declared != actual) {
            report.add(9, "BodyLength mismatch: got " + declared + ", expected " + actual);
        }
    }

    /** Validates tag 10 using the FIX modulo-256 checksum. */
    private void validateChecksum(String raw, Map<Integer, FixField> fields, ValidationReport report) {
        FixField checksum = fields.get(10);
        if (checksum == null) {
            report.add(10, "Missing required tag 10 (CheckSum)");
            return;
        }
        int checksumMarker = raw.lastIndexOf(FixParser.SOH + "10=");
        if (checksumMarker < 0) {
            return;
        }
        int total = 0;
        for (int index = 0; index <= checksumMarker; index++) {
            total += raw.charAt(index);
        }
        int expected = total % 256;
        int declared = checksum.parseIntValue(-1);
        if (declared != expected) {
            report.add(10, "Checksum mismatch: got " + String.format("%03d", declared) + ", expected " + String.format("%03d", expected));
        }
    }
}
