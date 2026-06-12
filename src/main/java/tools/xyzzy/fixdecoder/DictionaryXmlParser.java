// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Secure DOM parser that converts QuickFIX XML specifications into immutable dictionaries.
 */
final class DictionaryXmlParser {
    /** Parses a dictionary XML file bundled in the application resources. */
    FixDictionary parseResource(String resourceName, String source) {
        try (InputStream in = DictionaryXmlParser.class.getResourceAsStream("/" + resourceName)) {
            // A missing bundled dictionary is a packaging error, so fail fast.
            if (in == null) {
                throw new IllegalArgumentException("missing resource: " + resourceName);
            }
            return parse(in, source);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read " + resourceName, ex);
        }
    }

    /** Parses a custom dictionary path supplied via --xml. */
    FixDictionary parsePath(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(in, path.toString());
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read " + path, ex);
        }
    }

    /** Parses XML into dictionaries while disabling external entity expansion. */
    private FixDictionary parse(InputStream input, String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(input);
            Element root = document.getDocumentElement();
            String type = attr(root, "type", "FIX");
            String major = attr(root, "major", "");
            String minor = attr(root, "minor", "");
            String servicePack = attr(root, "servicepack", "");
            String key = normaliseKey(type, major, minor, servicePack);

            Map<Integer, FixDictionary.FieldDef> byTag = FixDictionary.newFieldTagMap();
            Map<String, FixDictionary.FieldDef> byName = new LinkedHashMap<>();
            for (Element field : children(required(root, "fields"), "field")) {
                Map<String, String> enums = new LinkedHashMap<>();
                for (Element child : elementChildren(field)) {
                    // QuickFIX specs use both direct <value> nodes and a <values> wrapper.
                    if ("value".equals(child.getTagName())) {
                        enums.put(attr(child, "enum", ""), attr(child, "description", ""));
                    } else if ("values".equals(child.getTagName())) {
                        for (Element value : children(child, "value")) {
                            enums.put(attr(value, "enum", ""), attr(value, "description", ""));
                        }
                    }
                }
                FixDictionary.FieldDef def = new FixDictionary.FieldDef(
                        Integer.parseInt(attr(field, "number", "0")),
                        attr(field, "name", ""),
                        attr(field, "type", ""),
                        enums);
                byTag.put(def.number(), def);
                byName.put(def.name(), def);
            }

            Map<String, FixDictionary.ComponentDef> components = new LinkedHashMap<>();
            for (Element component : children(required(root, "components"), "component")) {
                FixDictionary.ComponentDef def = new FixDictionary.ComponentDef(
                        attr(component, "name", ""),
                        parseEntries(component));
                components.put(def.name(), def);
            }

            FixDictionary.ComponentDef header =
                    new FixDictionary.ComponentDef("Header", parseEntries(required(root, "header")));
            FixDictionary.ComponentDef trailer =
                    new FixDictionary.ComponentDef("Trailer", parseEntries(required(root, "trailer")));

            Map<String, FixDictionary.MessageDef> byType = new LinkedHashMap<>();
            Map<String, FixDictionary.MessageDef> byMessageName = new LinkedHashMap<>();
            for (Element message : children(required(root, "messages"), "message")) {
                FixDictionary.MessageDef def = new FixDictionary.MessageDef(
                        attr(message, "name", ""),
                        attr(message, "msgtype", ""),
                        attr(message, "msgcat", ""),
                        parseEntries(message));
                byType.put(def.msgType(), def);
                byMessageName.put(def.name(), def);
            }

            FixDictionary dictionary = FixDictionary.builder()
                    .metadata(key, type, major, minor, servicePack, source)
                    .fields(byTag, byName)
                    .messages(byType, byMessageName)
                    .components(components)
                    .boundaries(header, trailer)
                    .build();
            resolveMessageShapes(dictionary);
            return dictionary;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new IllegalArgumentException("failed to parse FIX dictionary from " + source, ex);
        }
    }

    /** Resolves flattened required/order metadata for every message. */
    private void resolveMessageShapes(FixDictionary dictionary) {
        for (FixDictionary.MessageDef message : dictionary.messages()) {
            List<Integer> required = new ArrayList<>();
            List<Integer> order = new ArrayList<>();
            collectShape(dictionary, dictionary.header().entries(), required, order);
            collectShape(dictionary, message.entries(), required, order);
            collectShape(dictionary, dictionary.trailer().entries(), required, order);
            message.setResolvedShape(dedupe(required), dedupe(order));
        }
    }

    /** Recursively expands fields, components, and groups into validation metadata. */
    private void collectShape(
            FixDictionary dictionary,
            List<FixDictionary.Entry> entries,
            List<Integer> required,
            List<Integer> order) {
        for (FixDictionary.Entry entry : entries) {
            switch (entry) {
                case FixDictionary.FieldEntry(FixDictionary.FieldRef(String fieldName, boolean fieldRequired)) ->
                        collectFieldShape(dictionary, fieldName, fieldRequired, required, order);
                case FixDictionary.ComponentEntry(FixDictionary.ComponentRef(String componentName, boolean ignored)) ->
                        collectComponentShape(dictionary, componentName, required, order);
                case FixDictionary.GroupEntry(FixDictionary.GroupDef group) ->
                        collectGroupShape(dictionary, group, required, order);
            }
        }
    }

    /** Adds a field reference to flattened order and required-tag metadata. */
    private void collectFieldShape(
            FixDictionary dictionary,
            String fieldName,
            boolean fieldRequired,
            List<Integer> required,
            List<Integer> order) {
        FixDictionary.FieldDef field = dictionary.field(fieldName);
        // Unknown field references are ignored so custom dictionaries can be permissive.
        if (field == null) {
            return;
        }
        order.add(field.number());
        if (fieldRequired) {
            required.add(field.number());
        }
    }

    /** Expands a component reference into flattened metadata. */
    private void collectComponentShape(
            FixDictionary dictionary,
            String componentName,
            List<Integer> required,
            List<Integer> order) {
        FixDictionary.ComponentDef component = dictionary.component(componentName);
        // Components expand inline because the runtime validator works with tags.
        if (component != null) {
            collectShape(dictionary, component.entries(), required, order);
        }
    }

    /** Adds a group counter field and recursively expands nested group entries. */
    private void collectGroupShape(
            FixDictionary dictionary,
            FixDictionary.GroupDef group,
            List<Integer> required,
            List<Integer> order) {
        // The group name is the NumInGroup field in QuickFIX XML.
        collectFieldShape(dictionary, group.name(), group.required(), required, order);
        collectShape(dictionary, group.entries(), required, order);
    }

    /** Deduplicates tags while preserving first-seen dictionary order. */
    private List<Integer> dedupe(List<Integer> values) {
        List<Integer> out = new ArrayList<>(values.size());
        for (Integer value : values) {
            // Preserve the first occurrence because it reflects the outer container order.
            if (!out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    /** Parses ordered child entries for messages, components, and groups. */
    private List<FixDictionary.Entry> parseEntries(Element parent) {
        List<FixDictionary.Entry> entries = new ArrayList<>();
        for (Element child : elementChildren(parent)) {
            switch (child.getTagName()) {
                case "field" -> entries.add(new FixDictionary.FieldEntry(
                        new FixDictionary.FieldRef(attr(child, "name", ""), requiredFlag(child))));
                case "component" -> entries.add(new FixDictionary.ComponentEntry(
                        new FixDictionary.ComponentRef(attr(child, "name", ""), requiredFlag(child))));
                case "group" -> entries.add(new FixDictionary.GroupEntry(
                        new FixDictionary.GroupDef(attr(child, "name", ""), requiredFlag(child), parseEntries(child))));
                default -> {
                    // QuickFIX specs may contain comments or extension nodes; parser ignores them.
                }
            }
        }
        return entries;
    }

    /** Converts QuickFIX required="Y" attributes into booleans. */
    private boolean requiredFlag(Element element) {
        return "Y".equalsIgnoreCase(attr(element, "required", "N"));
    }

    /** Builds canonical dictionary keys, omitting SP0 to match the Rust implementation. */
    private static String normaliseKey(String type, String major, String minor, String servicePack) {
        // FIXT session dictionaries use a FIXT prefix rather than FIX.
        if ("FIXT".equalsIgnoreCase(type)) {
            return "FIXT" + major + minor;
        }
        boolean servicePackSuffix = servicePack != null && !servicePack.isBlank() && !"0".equals(servicePack);
        return ("FIX" + major + minor + (servicePackSuffix ? "SP" + servicePack : ""))
                .toUpperCase();
    }

    /** Returns the first required child element or fails with a helpful parser error. */
    private static Element required(Element root, String tag) {
        return children(root, tag).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing <" + tag + "> section"));
    }

    /** Returns direct child elements with a matching tag name. */
    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Element element : elementChildren(parent)) {
            // DOM includes whitespace text nodes; only element children are considered.
            if (tag.equals(element.getTagName())) {
                out.add(element);
            }
        }
        return out;
    }

    /** Returns all direct element children, skipping whitespace/text nodes. */
    private static List<Element> elementChildren(Element parent) {
        List<Element> out = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                out.add(element);
            }
        }
        return out;
    }

    /** Reads and ASCII-sanitises an XML attribute with a fallback. */
    private static String attr(Element element, String name, String fallback) {
        String value = element.getAttribute(name);
        // Missing or blank attributes use the caller's fallback.
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            // Match the Rust parser's ASCII sanitisation for generated display text.
            out.append(ch <= 0x7F ? ch : '?');
        }
        return out.toString();
    }
}
