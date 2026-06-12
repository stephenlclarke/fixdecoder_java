package tools.xyzzy.fixdecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests shell-like default argument parsing. */
class DefaultArgumentsTest {
    /** Quoted values should stay grouped while plain whitespace splits arguments. */
    @Test
    void splitPreservesQuotedValues() {
        assertEquals(
                List.of("--style=full", "--pager=less -R", "--delimiter=|"),
                DefaultArguments.split("--style=full --pager='less -R' --delimiter=|"));
    }
}
