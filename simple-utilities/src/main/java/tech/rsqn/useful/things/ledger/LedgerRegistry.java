package tech.rsqn.useful.things.ledger;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Registry for EventLedger instances.
 * Pure lookup table, no lifecycle management.
 */
public class LedgerRegistry {
    private final Map<EventType, EventLedger> ledgers = new HashMap<>();
    private final Path ledgerDir;
    private final ExecutorService sharedExecutor;

    public LedgerRegistry(LedgerConfig config, Path ledgerDir) {
        this(config, ledgerDir, null);
    }

    public LedgerRegistry(LedgerConfig config, Path ledgerDir, ExecutorService sharedExecutor) {
        this.ledgerDir = ledgerDir;
        this.sharedExecutor = sharedExecutor != null ? sharedExecutor : Executors.newCachedThreadPool();
        initializeLedgers(config);
    }

    private void initializeLedgers(LedgerConfig config) {
        Map<EventType, String> filenameMap = new HashMap<>();
        filenameMap.put(EventType.PRICE_UPDATE, "market_data.jsonl");
        filenameMap.put(EventType.CANDLE_UPDATE, "market_candles.jsonl");
        filenameMap.put(EventType.PORTFOLIO_UPDATE, "portfolio_state.jsonl");
        filenameMap.put(EventType.TRADE_PLACEMENT, "trade_placement.jsonl");
        filenameMap.put(EventType.TRADE_FILLS, "trade_fills.jsonl");
        filenameMap.put(EventType.VIRTUAL_ORDERS, "virtual_orders.jsonl");
        filenameMap.put(EventType.SYSTEM_EVENT, "system_events.jsonl");
        filenameMap.put(EventType.MARKET_ANALYSIS, "market_analysis.jsonl");
        filenameMap.put(EventType.COINBASE_ORDER_PLACEMENT, "coinbase_order_placement.jsonl");
        filenameMap.put(EventType.COINBASE_ORDER_STATUS, "coinbase_order_status.jsonl");
        filenameMap.put(EventType.COINBASE_FILL_HISTORY, "coinbase_fill_history.jsonl");
        filenameMap.put(EventType.TRADE_MATCH, "market_trades.jsonl");
        filenameMap.put(EventType.ORDER_BOOK_SNAPSHOT, "market_order_books.jsonl");
        filenameMap.put(EventType.TRADING_DECISION, "trading_decision.jsonl");
        filenameMap.put(EventType.SYSTEM_COMMAND, "system_command.jsonl");

        for (EventType type : EventType.values()) {
            String filename = filenameMap.getOrDefault(type, type.getValue() + ".jsonl");
            Path ledgerFile = ledgerDir.resolve(filename);
            ledgers.put(type, new EventLedger(type, ledgerFile, config, sharedExecutor));
        }
    }

    public EventLedger getLedger(EventType type) {
        return ledgers.get(type);
    }

    public Collection<EventLedger> getAllLedgers() {
        return Collections.unmodifiableCollection(ledgers.values());
    }
}
