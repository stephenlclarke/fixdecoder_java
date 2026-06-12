package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/** Tests Rust-compatible message rendering. */
class PrettifierTest {
    /** A heartbeat should render the same plain layout as the Rust reference. */
    @Test
    void prettifiesHeartbeat() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());
        FixDictionary dictionary = new DictionaryRegistry().resolve("44");
        StringWriter buffer = new StringWriter();

        new Prettifier().print(message, new FixTagLookup(dictionary), null, new PrintWriter(buffer));

        assertEquals(
                raw + System.lineSeparator()
                        + System.lineSeparator()
                        + "     8 (BeginString): FIX.4.4" + System.lineSeparator()
                        + "     9 (BodyLength): 005" + System.lineSeparator()
                        + "    35 (MsgType): 0 (HEARTBEAT)" + System.lineSeparator()
                        + "    10 (CheckSum): 000" + System.lineSeparator()
                        + System.lineSeparator(),
                buffer.toString());
    }
}
