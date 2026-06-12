package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.List;

/**
 * Renders dictionary information in the same table-oriented style as the Rust CLI.
 */
final class DictionaryDisplay {
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
            printEnums(field, 4, out);
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
        out.println("Component: " + Ansi.name(component.name(), colours));
        printEntries(dictionary, component.entries(), 4, verbose, out);
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
                Ansi.name(message.name(), colours),
                Ansi.tag(message.msgType(), colours));
        if (includeHeader) {
            out.println("    Component: " + Ansi.name("Header", colours));
            printEntries(dictionary, dictionary.header().entries(), 8, verbose, out);
        }
        out.println("    Message: " + Ansi.name("Body", colours));
        printEntries(dictionary, message.entries(), 8, verbose, out);
        if (includeTrailer) {
            out.println("    Component: " + Ansi.name("Trailer", colours));
            printEntries(dictionary, dictionary.trailer().entries(), 8, verbose, out);
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
                case FixDictionary.FieldEntry fieldEntry -> {
                    FixDictionary.FieldDef field = dictionary.field(fieldEntry.field().name());
                    if (field != null) {
                        printField(field, indent, fieldEntry.field().required(), out);
                        if (verbose) {
                            printEnums(field, indent + 4, out);
                        }
                    }
                }
                case FixDictionary.ComponentEntry componentEntry -> {
                    FixDictionary.ComponentDef component = dictionary.component(componentEntry.component().name());
                    if (component != null) {
                        out.printf("%sComponent: %s%n", spaces(indent), Ansi.name(component.name(), colours));
                        printEntries(dictionary, component.entries(), indent + 4, verbose, out);
                    }
                }
                case FixDictionary.GroupEntry groupEntry -> {
                    FixDictionary.FieldDef countField = dictionary.field(groupEntry.group().name());
                    if (countField != null) {
                        printField(countField, indent, groupEntry.group().required(), out);
                    }
                    printEntries(dictionary, groupEntry.group().entries(), indent + 4, verbose, out);
                }
            }
        }
    }

    /** Prints one field in the `tag: name (TYPE)` layout. */
    private void printField(FixDictionary.FieldDef field, int indent, boolean required, PrintWriter out) {
        out.printf(
                "%s%s: %s (%s)",
                spaces(indent),
                Ansi.tag(String.format("%4d", field.number()), colours),
                Ansi.name(field.name(), colours),
                Ansi.type(field.type(), colours));
        if (required) {
            out.printf(" - (%s)", Ansi.error("Y", colours));
        }
        out.println();
    }

    /** Prints enum values under a field when verbose output is requested. */
    private void printEnums(FixDictionary.FieldDef field, int indent, PrintWriter out) {
        field.enums().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .forEach(entry -> out.printf("%s%s = %s%n", spaces(indent), entry.getKey(), entry.getValue()));
    }

    /** Allocates indentation strings only in user-facing display paths. */
    private String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }

    /** Stable enum sorter that keeps numeric-looking enum values in lexical order. */
    private enum MapEntryComparator implements java.util.Comparator<java.util.Map.Entry<String, String>> {
        INSTANCE;

        @Override
        public int compare(java.util.Map.Entry<String, String> left, java.util.Map.Entry<String, String> right) {
            return left.getKey().compareTo(right.getKey());
        }
    }
}
