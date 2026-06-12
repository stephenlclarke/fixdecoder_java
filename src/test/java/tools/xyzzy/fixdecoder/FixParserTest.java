package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests reusable FIX message parsing. */
class FixParserTest {
    /** A SOH-delimited message should parse into ordered tag/value fields. */
    @Test
    void parseExtractsOrderedFields() {
        String raw = "8=FIX.4.4\u00019=005\u000135=0\u000110=000\u0001";
        FixMessage message = new FixParser().parseInto(raw, new FixMessage());

        assertEquals(4, message.fields().size());
        assertEquals("FIX.4.4", message.valueOf(8));
        assertEquals("0", message.valueOf(35));
        assertTrue(message.fields().get(2).valueEquals("0"));
    }
}
