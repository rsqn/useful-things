package tech.rsqn.useful.things.ledger;

import java.util.HashMap;
import java.util.Map;

/**
 * Event types supported by the ledger system.
 */
public enum EventType {
    PRICE_UPDATE("price_update"),
    CANDLE_UPDATE("candle_update"),
    PORTFOLIO_UPDATE("portfolio_update"),
    TRADE_PLACEMENT("trade_placement"),
    TRADE_FILLS("trade_fills"),
    VIRTUAL_ORDERS("virtual_orders"),
    SYSTEM_EVENT("system_event"),
    MARKET_ANALYSIS("market_analysis"),
    COINBASE_ORDER_PLACEMENT("coinbase_order_placement"),
    COINBASE_ORDER_STATUS("coinbase_order_status"),
    COINBASE_FILL_HISTORY("coinbase_fill_history"),
    TRADING_DECISION("trading_decision"),
    TRADE_EXECUTION("trade_execution"),
    TRADE_MATCH("trade_match"),
    ORDER_BOOK_SNAPSHOT("order_book_snapshot"),
    MARKET_REGIME("market_regime"),
    SYSTEM_COMMAND("system_command");

    private final String value;
    private static final Map<String, EventType> BY_VALUE = new HashMap<>();

    static {
        for (EventType type : values()) {
            BY_VALUE.put(type.value, type);
        }
    }

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EventType fromValue(String value) {
        EventType type = BY_VALUE.get(value);
        if (type == null) {
            throw new IllegalArgumentException("Unknown EventType: " + value);
        }
        return type;
    }
}
