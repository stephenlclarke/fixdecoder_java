// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;

/**
 * Renders dictionary information in the same table-oriented style as the Rust CLI.
 */
final class DictionaryDisplay {
    private static final int TAG_WIDTH = 4;
    private static final int FIELD_NAME_OFFSET = TAG_WIDTH + 2;
    private static final int MESSAGE_FIELD_INDENT = 8;
    private static final int ROOT_COMPONENT_FIELD_INDENT = 4;
    private static final int GROUP_CHILD_FIELD_INDENT = 6;
    private static final int COMPONENT_LABEL_OUTDENT = 5;
    private static final Comparator<java.util.Map.Entry<String, String>> ENUM_BY_KEY =
            java.util.Map.Entry.comparingByKey();

    private final DictionaryRegistry registry;
    private final boolean colours;

    /** Creates a renderer for a registry and colour mode. */
    DictionaryDisplay(DictionaryRegistry registry, boolean colours) {
        this.registry = registry;
        this.colours = colours;
    }

    /** Prints the loaded dictionary summary table. */
    void printInfo(FixDictionary selected, PrintWriter out) {
        out.println("Available FIX Dictionaries: " + registry.availableKeys());
        out.println();
        out.println("Loaded dictionaries:");
        out.println("   Version     ServicePack   Fields  Components    Messages Source");
        for (DictionaryRegistry.DictionaryEntry entry : registry.entries()) {
            FixDictionary dictionary = entry.dictionary();
            String marker = entry.key().equals(selected.key()) ? "*" : " ";
            out.printf(
                    "%3s%-10s %12s %8d %11d %11d %s%n",
                    marker,
                    entry.key(),
                    dictionary.servicePack(),
                    dictionary.fieldCount(),
                    dictionary.componentCount(),
                    dictionary.messageCount(),
                    entry.source());
        }
        out.println();
        out.flush();
    }

    /** Prints a tag lookup or all tags when no tag number is supplied. */
    void printTag(FixDictionary dictionary, Integer tag, boolean verbose, PrintWriter out) {
        if (tag == null) {
            for (FixDictionary.FieldDef field : dictionary.fields()) {
                printField(field, 0, false, out);
            }
            out.flush();
            return;
        }
        FixDictionary.FieldDef field = dictionary.field(tag);
        if (field == null) {
            out.printf("Tag %d not found in %s%n", tag, dictionary.key());
            out.flush();
            return;
        }
        printField(field, 0, false, out);
        if (verbose) {
            printEnums(field, fieldNameIndent(0), out);
        }
        out.flush();
    }

    /** Prints a component lookup or all component names. */
    void printComponent(FixDictionary dictionary, String name, boolean verbose, PrintWriter out) {
        if (name == null || name.isBlank()) {
            for (FixDictionary.ComponentDef component : dictionary.components()) {
                out.println(component.name());
            }
            out.flush();
            return;
        }
        FixDictionary.ComponentDef component = dictionary.component(name);
        if (component == null) {
            out.printf("Component %s not found in %s%n", name, dictionary.key());
            out.flush();
            return;
        }
        out.println("Component: " + Ansi.colorName(component.name(), colours));
        printEntries(dictionary, component.entries(), ROOT_COMPONENT_FIELD_INDENT, verbose, out);
        out.flush();
    }

    /** Prints a message lookup by name or MsgType, or all messages when omitted. */
    void printMessage(
            FixDictionary dictionary,
            String nameOrType,
            boolean verbose,
            boolean includeHeader,
            boolean includeTrailer,
            PrintWriter out) {
        if (nameOrType == null || nameOrType.isBlank()) {
            for (FixDictionary.MessageDef message : dictionary.messages()) {
                out.printf("%s (%s)%n", message.name(), message.msgType());
            }
            out.flush();
            return;
        }
        FixDictionary.MessageDef message = dictionary.message(nameOrType);
        if (message == null) {
            out.printf("Message %s not found in %s%n", nameOrType, dictionary.key());
            out.flush();
            return;
        }
        out.printf(
                "Message: %s (%s)%n",
                Ansi.colorName(message.name(), colours),
                Ansi.colorTag(message.msgType(), colours));
        if (includeHeader) {
            out.println("    Component: " + Ansi.colorName("Header", colours));
            printEntries(dictionary, dictionary.header().entries(), MESSAGE_FIELD_INDENT, verbose, out);
        }
        out.println("    Message: " + Ansi.colorName("Body", colours));
        printEntries(dictionary, message.entries(), MESSAGE_FIELD_INDENT, verbose, out);
        if (includeTrailer) {
            out.println("    Component: " + Ansi.colorName("Trailer", colours));
            printEntries(dictionary, dictionary.trailer().entries(), MESSAGE_FIELD_INDENT, verbose, out);
        }
        out.flush();
    }

    /** Recursively prints fields, components, and repeating groups. */
    private void printEntries(
            FixDictionary dictionary,
            List<FixDictionary.Entry> entries,
            int indent,
            boolean verbose,
            PrintWriter out) {
        for (FixDictionary.Entry entry : entries) {
            switch (entry) {
                case FixDictionary.FieldEntry(FixDictionary.FieldRef(String fieldName, boolean required)) ->
                        printFieldEntry(dictionary, fieldName, required, indent, verbose, out);
                case FixDictionary.ComponentEntry(FixDictionary.ComponentRef(String componentName, boolean ignored)) ->
                        printComponentEntry(dictionary, componentName, indent, verbose, out);
                case FixDictionary.GroupEntry(FixDictionary.GroupDef group) ->
                        printGroupEntry(dictionary, group, indent, verbose, out);
            }
        }
    }

    /** Prints a field reference when it resolves in the selected dictionary. */
    private void printFieldEntry(
            FixDictionary dictionary,
            String fieldName,
            boolean required,
            int indent,
            boolean verbose,
            PrintWriter out) {
        FixDictionary.FieldDef field = dictionary.field(fieldName);
        // Custom dictionaries may contain unresolved references, matching the Rust decoder's leniency.
        if (field == null) {
            return;
        }
        printField(field, indent, required, out);
        if (verbose) {
            printEnums(field, fieldNameIndent(indent), out);
        }
    }

    /** Prints and expands a component reference when available. */
    private void printComponentEntry(
            FixDictionary dictionary,
            String componentName,
            int indent,
            boolean verbose,
            PrintWriter out) {
        FixDictionary.ComponentDef component = dictionary.component(componentName);
        // Missing component references are ignored for permissive custom XML support.
        if (component == null) {
            return;
        }
        out.printf("%sComponent: %s%n", spaces(componentLabelIndent(indent)), Ansi.colorName(component.name(), colours));
        printEntries(dictionary, component.entries(), indent, verbose, out);
    }

    /** Prints a repeating group count field and its child entries. */
    private void printGroupEntry(
            FixDictionary dictionary,
            FixDictionary.GroupDef group,
            int indent,
            boolean verbose,
            PrintWriter out) {
        FixDictionary.FieldDef countField = dictionary.field(group.name());
        // The count field may be absent in partial/custom dictionaries; still show nested known fields.
        if (countField != null) {
            printField(countField, indent, group.required(), out);
        }
        printEntries(dictionary, group.entries(), indent + GROUP_CHILD_FIELD_INDENT, verbose, out);
    }

    /** Prints one field in the `tag: name (TYPE)` layout. */
    private void printField(FixDictionary.FieldDef field, int indent, boolean required, PrintWriter out) {
        out.printf(
                "%s%s: %s (%s)",
                spaces(indent),
                Ansi.colorTag(String.format("%" + TAG_WIDTH + "d", field.number()), colours),
                Ansi.colorName(field.name(), colours),
                Ansi.colorType(field.type(), colours));
        if (required) {
            out.printf(" - (%s)", Ansi.colorError("Y", colours));
        }
        out.println();
    }

    /** Prints enum values under a field when verbose output is requested. */
    private void printEnums(FixDictionary.FieldDef field, int indent, PrintWriter out) {
        int enumWidth = field.enums().keySet().stream().mapToInt(String::length).max().orElse(1);
        field.enums().entrySet().stream()
                .sorted(ENUM_BY_KEY)
                .forEach(entry -> out.printf(
                        "%s%s : %s%n",
                        spaces(indent),
                        Ansi.colorValue(String.format("%" + enumWidth + "s", entry.getKey()), colours),
                        Ansi.colorEnum(entry.getValue(), colours)));
    }

    /** Allocates indentation strings only in user-facing display paths. */
    private String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }

    /** Places component labels to the left of the field tag column for the current container. */
    private int componentLabelIndent(int fieldIndent) {
        return Math.max(0, fieldIndent - COMPONENT_LABEL_OUTDENT);
    }

    /** Returns the visible column where field names start for a tag line. */
    private int fieldNameIndent(int fieldIndent) {
        return fieldIndent + FIELD_NAME_OFFSET;
    }

}
