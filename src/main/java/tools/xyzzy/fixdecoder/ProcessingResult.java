// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.nio.file.Path;

/**
 * Captures worker output location and counts so parallel file decoding can emit in argv order.
 *
 * @param output captured worker output file
 * @param counts message counts captured by the worker
 */
record ProcessingResult(Path output, MessageCounts counts) {
}
