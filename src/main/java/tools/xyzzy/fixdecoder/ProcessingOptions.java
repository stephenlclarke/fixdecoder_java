package tools.xyzzy.fixdecoder;

import java.nio.file.Path;
import java.util.List;

/**
 * Immutable decode options shared by stdin, file, and worker processing paths.
 */
/**
 * @param defaultDictionary dictionary used when the message does not name a known BeginString
 * @param delimiter input delimiter, normally SOH
 * @param validate whether protocol validation should run
 * @param secret whether sensitive tags are obfuscated in decode output
 * @param noCounts whether the final message count table is suppressed
 * @param summary whether compact order summaries are emitted
 * @param follow whether follow-mode flushing is enabled
 * @param files ordered input file list
 */
record ProcessingOptions(
        FixDictionary defaultDictionary,
        char delimiter,
        boolean validate,
        boolean secret,
        boolean noCounts,
        boolean summary,
        boolean follow,
        List<Path> files) {
}
