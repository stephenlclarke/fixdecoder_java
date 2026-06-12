// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

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
