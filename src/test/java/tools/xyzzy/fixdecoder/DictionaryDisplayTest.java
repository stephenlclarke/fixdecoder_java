package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** Tests dictionary display modes used by the CLI. */
class DictionaryDisplayTest {
    /** Info mode should include aliases, active marker, and built-in source text. */
    @Test
    void printInfoIncludesLoadedDictionaries() {
        DictionaryRegistry registry = new DictionaryRegistry();
        StringWriter buffer = new StringWriter();

        new DictionaryDisplay(registry, false).printInfo(registry.resolve("44"), new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("Available FIX Dictionaries: FIX27,FIX30,FIX40"));
        assertTrue(buffer.toString().contains("*FIX44"));
    }

    /** Tag mode should render field metadata and verbose enum values. */
    @Test
    void printTagIncludesEnumsWhenVerbose() {
        DictionaryRegistry registry = new DictionaryRegistry();
        StringWriter buffer = new StringWriter();

        new DictionaryDisplay(registry, false).printTag(registry.resolve("44"), 35, true, new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("MsgType"));
        assertTrue(buffer.toString().contains("HEARTBEAT"));
    }

    /** Message and component modes should recurse through fields and components. */
    @Test
    void printMessageAndComponentDefinitions() {
        DictionaryRegistry registry = new DictionaryRegistry();
        DictionaryDisplay display = new DictionaryDisplay(registry, false);
        StringWriter buffer = new StringWriter();

        display.printMessage(registry.resolve("44"), "D", false, true, true, new PrintWriter(buffer));
        display.printComponent(registry.resolve("44"), "Instrument", false, new PrintWriter(buffer));

        assertTrue(buffer.toString().contains("Message: NewOrderSingle (D)"));
        assertTrue(buffer.toString().contains("Component: Header"));
        assertTrue(buffer.toString().contains("Component: Instrument"));
    }

    /** List modes and missing items should produce deterministic output. */
    @Test
    void listAndMissingModesReturnHelpfulOutput() {
        DictionaryRegistry registry = new DictionaryRegistry();
        DictionaryDisplay display = new DictionaryDisplay(registry, false);
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        display.printMessage(registry.resolve("44"), null, false, false, false, writer);
        display.printComponent(registry.resolve("44"), null, false, writer);
        display.printTag(registry.resolve("44"), 999999, false, writer);

        assertTrue(buffer.toString().contains("NewOrderSingle"));
        assertTrue(buffer.toString().contains("Instrument"));
        assertTrue(buffer.toString().contains("Tag 999999 not found"));
    }
}
