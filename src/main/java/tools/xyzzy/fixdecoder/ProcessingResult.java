package tools.xyzzy.fixdecoder;

/**
 * Captures worker output and counts so parallel file decoding can emit in argv order.
 */
/**
 * @param stdout captured worker output
 * @param counts message counts captured by the worker
 */
record ProcessingResult(String stdout, MessageCounts counts) {
}
