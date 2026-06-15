// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2026 Steve Clarke <stephenlclarke@mac.com> - https://xyzzy.tools

package tools.xyzzy.fixdecoder;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maintains compact order lifecycle state for the summary decoding mode.
 */
final class OrderSummaryTracker {
    private static final int[] ORDER_KEY_TAGS = {37, 11, 41};
    private static final int MAX_TRACKED_ORDERS = 10_000;

    private final Map<String, OrderState> orders = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();

    /** Records one application message and prints the updated order state when it is order flow. */
    void accept(FixMessage message, FixTagLookup lookup, PrintWriter out) {
        String key = resolveKey(message);
        if (key == null) {
            // Messages without any order identifier are not actionable order-flow summaries.
            return;
        }
        OrderState state = orders.computeIfAbsent(key, OrderState::new);
        state.update(message, lookup);
        indexAliases(key, message);
        state.print(out);
        if (state.terminal()) {
            removeOrder(key);
        } else {
            evictEldestOrderIfNeeded();
        }
    }

    /** Chooses a stable order key using OrderID, ClOrdID, then OrigClOrdID aliases. */
    private String resolveKey(FixMessage message) {
        String fallback = null;
        for (int tag : ORDER_KEY_TAGS) {
            String value = message.valueOf(tag);
            if (value != null && !value.isBlank()) {
                String alias = aliases.get(value);
                if (alias != null) {
                    return alias;
                }
                if (fallback == null) {
                    fallback = value;
                }
            }
        }
        return fallback;
    }

    /** Maps every seen order identifier to the chosen canonical key. */
    private void indexAliases(String key, FixMessage message) {
        for (int tag : ORDER_KEY_TAGS) {
            String value = message.valueOf(tag);
            if (value != null && !value.isBlank()) {
                aliases.put(value, key);
            }
        }
    }

    /** Returns current order count for regression tests and memory assertions. */
    int trackedOrderCount() {
        return orders.size();
    }

    /** Removes the eldest open order when the configured memory bound is exceeded. */
    private void evictEldestOrderIfNeeded() {
        if (orders.size() <= MAX_TRACKED_ORDERS) {
            return;
        }
        Iterator<String> keys = orders.keySet().iterator();
        if (keys.hasNext()) {
            removeOrder(keys.next());
        }
    }

    /** Removes one order and every alias pointing to it. */
    private void removeOrder(String key) {
        orders.remove(key);
        aliases.entrySet().removeIf(entry -> key.equals(entry.getValue()));
    }

    /** Mutable lifecycle record for one order. */
    private static final class OrderState {
        private final String key;
        private int events;
        private String orderId;
        private String clOrdId;
        private String origClOrdId;
        private String message;
        private String execType;
        private String ordStatus;
        private String side;
        private String symbol;
        private String quantity;
        private String price;
        private String cumQuantity;
        private String leavesQuantity;
        private String lastQuantity;
        private String lastPrice;
        private String text;
        private boolean terminal;

        /** Creates an empty order state for a canonical key. */
        private OrderState(String key) {
            this.key = key;
        }

        /** Applies the latest fields from a FIX message while preserving older stable attributes. */
        private void update(FixMessage fixMessage, FixTagLookup lookup) {
            events++;
            message = describeMessage(fixMessage, lookup);
            orderId = latest(fixMessage.valueOf(37), orderId);
            clOrdId = latest(fixMessage.valueOf(11), clOrdId);
            origClOrdId = latest(fixMessage.valueOf(41), origClOrdId);
            execType = latest(describeEnum(lookup, 150, fixMessage.valueOf(150)), execType);
            ordStatus = latest(describeEnum(lookup, 39, fixMessage.valueOf(39)), ordStatus);
            side = latest(describeEnum(lookup, 54, fixMessage.valueOf(54)), side);
            symbol = latest(fixMessage.valueOf(55), symbol);
            quantity = latest(fixMessage.valueOf(38), quantity);
            price = latest(fixMessage.valueOf(44), price);
            cumQuantity = latest(fixMessage.valueOf(14), cumQuantity);
            leavesQuantity = latest(fixMessage.valueOf(151), leavesQuantity);
            lastQuantity = latest(fixMessage.valueOf(32), lastQuantity);
            lastPrice = latest(fixMessage.valueOf(31), lastPrice);
            text = latest(fixMessage.valueOf(58), text);
            terminal = terminalStatus(fixMessage.valueOf(39)) || terminalExecType(fixMessage.valueOf(150));
        }

        /** Prints the compact summary block for this order. */
        private void print(PrintWriter out) {
            out.println("Order Summary:");
            printLine(out, "Order", displayId());
            printLine(out, "Message", message);
            printLine(out, "OrderID", orderId);
            printLine(out, "ClOrdID", clOrdId);
            printLine(out, "OrigClOrdID", origClOrdId);
            printLine(out, "ExecType", execType);
            printLine(out, "OrdStatus", ordStatus);
            printLine(out, "Side", side);
            printLine(out, "Symbol", symbol);
            printLine(out, "OrderQty", quantity);
            printLine(out, "Price", price);
            printLine(out, "CumQty", cumQuantity);
            printLine(out, "LeavesQty", leavesQuantity);
            printLine(out, "LastQty", lastQuantity);
            printLine(out, "LastPx", lastPrice);
            printLine(out, "Text", text);
            printLine(out, "Events", Integer.toString(events));
            out.println();
        }

        /** Returns the best display identifier currently known for this order. */
        private String displayId() {
            if (orderId != null) {
                return orderId;
            }
            if (clOrdId != null) {
                return clOrdId;
            }
            return key;
        }

        /** Describes MsgType using the active dictionary when possible. */
        private String describeMessage(FixMessage fixMessage, FixTagLookup lookup) {
            String msgType = fixMessage.valueOf(35);
            FixDictionary.MessageDef def = msgType == null ? null : lookup.message(msgType);
            if (def == null) {
                return msgType;
            }
            return def.name() + " (" + msgType + ")";
        }

        /** Describes enum values as "value (description)" when the dictionary has metadata. */
        private String describeEnum(FixTagLookup lookup, int tag, String value) {
            if (value == null) {
                return null;
            }
            String description = lookup.enumDescription(tag, value);
            if (description == null || description.isBlank()) {
                return value;
            }
            return value + " (" + description + ")";
        }

        /** Keeps the newest nonblank value, otherwise the previous state. */
        private String latest(String next, String previous) {
            return next == null || next.isBlank() ? previous : next;
        }

        /** Returns true after a terminal order-state message has been printed. */
        private boolean terminal() {
            return terminal;
        }

        /** Checks OrdStatus values that close an order for summary-retention purposes. */
        private boolean terminalStatus(String value) {
            return "2".equals(value) || "3".equals(value) || "4".equals(value) || "8".equals(value) || "C".equals(value);
        }

        /** Checks ExecType values that close an order for summary-retention purposes. */
        private boolean terminalExecType(String value) {
            return "2".equals(value) || "3".equals(value) || "4".equals(value) || "8".equals(value) || "C".equals(value);
        }

        /** Prints one optional summary field. */
        private void printLine(PrintWriter out, String label, String value) {
            if (value != null && !value.isBlank()) {
                out.println("    " + label + ": " + value);
            }
        }
    }
}
