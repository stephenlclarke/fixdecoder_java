package tools.xyzzy.fixdecoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable in-memory representation of one QuickFIX XML dictionary.
 */
final class FixDictionary {
    private final String key;
    private final String type;
    private final String major;
    private final String minor;
    private final String servicePack;
    private final String source;
    private final Map<Integer, FieldDef> fieldsByTag;
    private final Map<String, FieldDef> fieldsByName;
    private final Map<String, MessageDef> messagesByType;
    private final Map<String, MessageDef> messagesByName;
    private final Map<String, ComponentDef> components;
    private final ComponentDef header;
    private final ComponentDef trailer;

    /** Creates a dictionary from parsed XML sections and precomputed lookup maps. */
    FixDictionary(
            String key,
            String type,
            String major,
            String minor,
            String servicePack,
            String source,
            Map<Integer, FieldDef> fieldsByTag,
            Map<String, FieldDef> fieldsByName,
            Map<String, MessageDef> messagesByType,
            Map<String, MessageDef> messagesByName,
            Map<String, ComponentDef> components,
            ComponentDef header,
            ComponentDef trailer) {
        this.key = key;
        this.type = type;
        this.major = major;
        this.minor = minor;
        this.servicePack = servicePack;
        this.source = source;
        this.fieldsByTag = Map.copyOf(fieldsByTag);
        this.fieldsByName = Map.copyOf(fieldsByName);
        this.messagesByType = Map.copyOf(messagesByType);
        this.messagesByName = Map.copyOf(messagesByName);
        this.components = Map.copyOf(components);
        this.header = header;
        this.trailer = trailer;
    }

    /** Returns the canonical dictionary key such as FIX44 or FIX50SP2. */
    String key() {
        return key;
    }

    /** Returns the source label shown in info output. */
    String source() {
        return source;
    }

    /** Returns the service pack number, using zero for classic FIX dictionaries. */
    String servicePack() {
        return servicePack == null || servicePack.isBlank() ? "0" : servicePack;
    }

    /** Returns the number of fields defined by the dictionary. */
    int fieldCount() {
        return fieldsByTag.size();
    }

    /** Returns component count including header and trailer pseudo-components. */
    int componentCount() {
        return components.size() + 2;
    }

    /** Returns the number of message definitions. */
    int messageCount() {
        return messagesByType.size();
    }

    /** Looks up a field by numeric tag. */
    FieldDef field(int tag) {
        return fieldsByTag.get(tag);
    }

    /** Looks up a field by FIX name. */
    FieldDef field(String name) {
        return fieldsByName.get(name);
    }

    /** Looks up a message by name or MsgType. */
    MessageDef message(String nameOrType) {
        MessageDef byType = messagesByType.get(nameOrType);
        return byType == null ? messagesByName.get(nameOrType) : byType;
    }

    /** Looks up a component, including Header and Trailer pseudo-components. */
    ComponentDef component(String name) {
        if ("Header".equals(name)) {
            return header;
        }
        if ("Trailer".equals(name)) {
            return trailer;
        }
        return components.get(name);
    }

    /** Returns the header pseudo-component. */
    ComponentDef header() {
        return header;
    }

    /** Returns the trailer pseudo-component. */
    ComponentDef trailer() {
        return trailer;
    }

    /** Returns message definitions sorted by message name for list output. */
    List<MessageDef> messages() {
        return messagesByType.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
    }

    /** Returns component definitions sorted by component name for list output. */
    List<ComponentDef> components() {
        return components.values().stream().sorted((a, b) -> a.name().compareTo(b.name())).toList();
    }

    /** Returns field definitions sorted by tag number for list output. */
    List<FieldDef> fields() {
        return fieldsByTag.values().stream().sorted((a, b) -> Integer.compare(a.number(), b.number())).toList();
    }

    /** Returns the FIX version display string. */
    String versionDisplay() {
        return type + major + minor + (servicePack == null || servicePack.isBlank() ? "" : "SP" + servicePack);
    }

    /** Field metadata and enum values from the <fields> section. */
    record FieldDef(int number, String name, String type, Map<String, String> enums) {
        /** Copies enum metadata into an immutable map. */
        FieldDef {
            enums = Map.copyOf(enums);
        }
    }

    /** Field reference inside a message/component/group container. */
    record FieldRef(String name, boolean required) {
    }

    /** Component reference inside a message/component/group container. */
    record ComponentRef(String name, boolean required) {
    }

    static final class GroupDef {
        private final String name;
        private final boolean required;
        private final List<Entry> entries;

        /** Creates a repeating group definition. */
        GroupDef(String name, boolean required, List<Entry> entries) {
            this.name = name;
            this.required = required;
            this.entries = List.copyOf(entries);
        }

        /** Returns the NumInGroup field name. */
        String name() {
            return name;
        }

        /** Returns whether the group is required in its parent container. */
        boolean required() {
            return required;
        }

        /** Returns group entry fields/components in dictionary order. */
        List<Entry> entries() {
            return entries;
        }
    }

    static final class ComponentDef {
        private final String name;
        private final List<Entry> entries;

        /** Creates a component definition. */
        ComponentDef(String name, List<Entry> entries) {
            this.name = name;
            this.entries = List.copyOf(entries);
        }

        /** Returns the component name. */
        String name() {
            return name;
        }

        /** Returns component entries in dictionary order. */
        List<Entry> entries() {
            return entries;
        }
    }

    static final class MessageDef {
        private final String name;
        private final String msgType;
        private final String category;
        private final List<Entry> entries;
        private final List<Integer> requiredTags;
        private final List<Integer> fieldOrder;

        /** Creates a message definition. */
        MessageDef(String name, String msgType, String category, List<Entry> entries) {
            this.name = name;
            this.msgType = msgType;
            this.category = category;
            this.entries = List.copyOf(entries);
            this.requiredTags = new ArrayList<>();
            this.fieldOrder = new ArrayList<>();
        }

        /** Returns the message name. */
        String name() {
            return name;
        }

        /** Returns the FIX MsgType value. */
        String msgType() {
            return msgType;
        }

        /** Returns the QuickFIX message category. */
        String category() {
            return category;
        }

        /** Returns message body entries. */
        List<Entry> entries() {
            return entries;
        }

        /** Returns required tags after expanding header, body, components, and trailer. */
        List<Integer> requiredTags() {
            return Collections.unmodifiableList(requiredTags);
        }

        /** Returns flattened dictionary tag order for validation. */
        List<Integer> fieldOrder() {
            return Collections.unmodifiableList(fieldOrder);
        }

        /** Stores flattened required and ordering metadata after XML parsing. */
        void setResolvedShape(List<Integer> required, List<Integer> order) {
            requiredTags.clear();
            requiredTags.addAll(required);
            fieldOrder.clear();
            fieldOrder.addAll(order);
        }
    }

    /** Marker for ordered dictionary container entries. */
    sealed interface Entry permits FieldEntry, ComponentEntry, GroupEntry {
    }

    /** Ordered field entry wrapper. */
    record FieldEntry(FieldRef field) implements Entry {
    }

    /** Ordered component entry wrapper. */
    record ComponentEntry(ComponentRef component) implements Entry {
    }

    /** Ordered repeating group entry wrapper. */
    record GroupEntry(GroupDef group) implements Entry {
    }

    /** Creates an insertion-ordered field map for XML parsing. */
    static Map<Integer, FieldDef> newFieldTagMap() {
        return new LinkedHashMap<>();
    }
}
