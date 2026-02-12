import json
import os
import sys
import threading
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from datetime import datetime, timezone, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Dict, Iterator, List, Optional

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from base_module import BaseModule, ErrorSeverity, ModuleError  # noqa: E402
from ledger.event_ledger import EventLedger  # noqa: E402
from ledger.event_types import BaseEvent, EventType  # noqa: E402
from utils.base_module import ServiceState  # noqa: E402


class DecimalEncoder(json.JSONEncoder):
    """JSON encoder that handles Decimal types."""

    def default(self, obj):
        if isinstance(obj, Decimal):
            return str(obj)  # Preserve precision as string
        return super().default(obj)


class LedgerManager(BaseModule):
    """Manager that coordinates multiple event-specific ledgers."""

    def __init__(
        self,
        config,
        ledger_dir: Optional[str] = None,
        notification_executor: Optional[Any] = None,
    ):
        super().__init__(config)
        # Determine ledger directory.
        env_dir = os.getenv("PYSOL_LEDGER_DATA_DIR") or os.getenv("PYSOL_DATA_DIR")
        if env_dir:
            self.ledger_dir = Path(env_dir)
        elif ledger_dir:
            self.ledger_dir = Path(ledger_dir)
        else:
            data_dir = (
                self.config.get_str("ledger.data_directory", "")
                or self.config.get_str("ledger_directory", "")
                or self.config.get_str("data_directory", "")
                or "data"
            )
            self.ledger_dir = Path(data_dir)

        self.ledger_dir.mkdir(parents=True, exist_ok=True)

        # System readiness and buffering
        self._system_ready = False
        self._buffered_events = []
        self._historical_data_loaded = False

        self.notification_executor = notification_executor or ThreadPoolExecutor(
            max_workers=10, thread_name_prefix="ledger-notify"
        )

        # Ledger instances
        self.ledgers: Dict[EventType, EventLedger] = {}
        self.subscribers = defaultdict(list)
        self._init_ledgers()

    def _init_ledgers(self):
        """Initialize separate ledger instances for each event type."""
        filename_map = {
            EventType.PRICE_UPDATE: "market_data.jsonl",
            EventType.CANDLE_UPDATE: "market_candles.jsonl",
            EventType.PORTFOLIO_UPDATE: "portfolio_state.jsonl",
            EventType.TRADE_PLACEMENT: "trade_placement.jsonl",
            EventType.TRADE_FILLS: "trade_fills.jsonl",
            EventType.VIRTUAL_ORDERS: "virtual_orders.jsonl",
            EventType.SYSTEM_EVENT: "system_events.jsonl",
            EventType.MARKET_ANALYSIS: "market_analysis.jsonl",
            EventType.COINBASE_ORDER_PLACEMENT: "coinbase_order_placement.jsonl",
            EventType.COINBASE_ORDER_STATUS: "coinbase_order_status.jsonl",
            EventType.COINBASE_FILL_HISTORY: "coinbase_fill_history.jsonl",
            EventType.TRADE_MATCH: "market_trades.jsonl",
            EventType.ORDER_BOOK_SNAPSHOT: "market_order_books.jsonl",
            EventType.TRADING_DECISION: "trading_decision.jsonl",
            EventType.SYSTEM_COMMAND: "system_command.jsonl",
        }

        print(f"🔧 LEDGER DEBUG: Initializing {len(EventType)} event-specific ledgers")

        for event_type in EventType:
            filename = filename_map.get(event_type, f"{event_type.value}.jsonl")
            ledger_file = self.ledger_dir / filename
            auto_flush = True
            # Pass the centralized notification executor to each individual ledger
            self.ledgers[event_type] = EventLedger(
                event_type,
                ledger_file,
                self.config,
                auto_flush=auto_flush,
                notification_executor=self.notification_executor,
                clock=self.clock,
            )

        print("✅ LEDGER DEBUG: All event ledgers initialized")

    def get_ledger(self, event_type: EventType) -> EventLedger:
        """Get the individual ledger instance for an event type."""
        return self.ledgers[event_type]

    def get_ledger_file(self, event_type: EventType) -> Path:
        """Compatibility shim: Return the file path for a given event type's ledger."""
        return self.ledgers[event_type].ledger_file

    def set_auto_flush(self, enabled: bool) -> None:
        """Set auto-flush for all ledgers."""
        for ledger in self.ledgers.values():
            ledger.auto_flush = enabled
            if enabled:
                ledger.force_flush()
        print(f"📋 Ledger: Set auto_flush={enabled} for all ledgers")

    @contextmanager
    def bulk_write_mode(self):
        """Context manager for bulk write operations across ALL ledgers.
        
        Suspends periodic flushing for high-throughput operations like priming.
        Flushes all ledgers on exit.
        
        Usage:
            with ledger_manager.bulk_write_mode():
                for event in massive_dataset:
                    ledger_manager.write_event(...)
            # Auto-flush happens on exit
        """
        print("📋 Ledger: Entering bulk write mode (periodic flushing suspended)")
        for ledger in self.ledgers.values():
            ledger._bulk_mode = True
        try:
            yield
        finally:
            print("📋 Ledger: Exiting bulk write mode (flushing all ledgers)")
            for ledger in self.ledgers.values():
                ledger._bulk_mode = False
                ledger.force_flush()

    def write_event(self, event_type: EventType, data: Dict[str, Any], timestamp: Optional[datetime] = None) -> int:
        """Write an immutable event to the ledger. Returns the event ID."""
        if not self._keep_running:
            return -1

        if not self._system_ready:
            self._buffered_events.append((event_type, data, timestamp or self.clock.now()))
            return -1

        return self.ledgers[event_type].write_event(data, timestamp=timestamp)

    def read_events(self, event_type: EventType, limit: Optional[int] = None) -> Iterator[BaseEvent]:
        """Read events from the ledger."""
        return self.ledgers[event_type].read_events(limit=limit)

    def read_events_stream(self, event_type: EventType) -> Iterator[BaseEvent]:
        """Stream events from the ledger."""
        return self.ledgers[event_type].read_events_stream()

    def read_events_stream_reverse(self, event_type: EventType) -> Iterator[BaseEvent]:
        """Stream events from the ledger in reverse."""
        return self.ledgers[event_type].read_events_stream_reverse()

    def read_events_filtered(
        self, event_type: EventType, filter_func: Callable[[Dict[str, Any]], bool]
    ) -> Iterator[BaseEvent]:
        """Read filtered events from the ledger as a stream."""
        return self.ledgers[event_type].read_events_filtered(filter_func)

    def stream_events_filtered(
        self, event_type: EventType, filter_func: Callable[[Dict[str, Any]], bool]
    ) -> Iterator[BaseEvent]:
        """Stream filtered events from the ledger."""
        return self.ledgers[event_type].stream_events_filtered(filter_func)

    def get_latest_event(self, event_type: EventType) -> Optional[BaseEvent]:
        """Get the latest event of a specific type."""
        return self.ledgers[event_type].get_latest_event()

    def subscribe(self, event_type: EventType, callback: Callable[[BaseEvent], None]) -> None:
        """Subscribe to real-time events."""
        self.ledgers[event_type].subscribe(callback)

    def enable_memory_ledger(self, event_type: EventType, filter_callback: Optional[Callable] = None) -> None:
        """Compatibility shim: Enable in-memory storage for this event type."""
        ledger = self.ledgers.get(event_type)
        if not ledger:
            return

        ledger.enable_memory_cache()

        # Load existing events into memory with filter
        # Use read_events() which might be mocked in tests
        events_to_cache = []
        for event in self.read_events(event_type):
            if filter_callback is None or filter_callback(event):
                events_to_cache.append(event)

        with ledger._memory_lock:
            ledger._memory_cache = events_to_cache

    def disable_memory_ledger(self, event_type: EventType) -> None:
        """Compatibility shim: Disable in-memory storage."""
        ledger = self.ledgers.get(event_type)
        if ledger:
            ledger.disable_memory_cache()

    def is_memory_enabled(self, event_type: EventType) -> bool:
        """Compatibility shim: Check if memory cache is enabled."""
        ledger = self.ledgers.get(event_type)
        return ledger._is_memory_enabled if ledger else False

    def get_memory_events(self, event_type: EventType) -> List[BaseEvent]:
        """Compatibility shim: Get memory cache events."""
        ledger = self.ledgers.get(event_type)
        if not ledger:
            return []
        with ledger._memory_lock:
            return list(ledger._memory_cache)

    def get_memory_events_filtered(self, event_type: EventType, filter_func: Callable) -> List[BaseEvent]:
        """Compatibility shim: Get filtered memory cache events."""
        ledger = self.ledgers.get(event_type)
        if not ledger:
            return []
        with ledger._memory_lock:
            return [ev for ev in ledger._memory_cache if filter_func(ev)]

    def stream_memory_events(self, event_type: EventType, processor: Callable) -> int:
        """Compatibility shim: Stream memory cache events."""
        ledger = self.ledgers.get(event_type)
        if not ledger:
            return 0
        count = 0
        with ledger._memory_lock:
            for ev in reversed(ledger._memory_cache):
                processor(ev)
                count += 1
        return count

    def load_historical_data(self):
        """Load historical data from existing files into RAM caches where enabled."""
        if self._historical_data_loaded:
            return

        cutoff_time = self.clock.now() - timedelta(hours=25)

        # Configurable memory ledgers - Only hydrate transactional/state events.
        # High-volume market data (PRICE/CANDLE) should be streamed from disk
        # directly into components that need them (like TradingPairManager)
        # to avoid duplicating millions of events in global RAM cache.
        memory_types = {
            EventType.TRADE_FILLS,
            EventType.TRADE_PLACEMENT,
            EventType.VIRTUAL_ORDERS,
            EventType.TRADE_EXECUTION,
        }

        print(f"📋 Ledger: Hydrating {len(memory_types)} ledgers into RAM...")
        
        # Report memory before hydration
        try:
            import psutil
            process = psutil.Process()
            mem_before = process.memory_info().rss / 1024 / 1024
            print(f"💾 MEMORY: Before hydration: {mem_before:.1f}MB")
        except Exception:
            pass
        
        for et in memory_types:
            count = self.ledgers[et].hydrate(cutoff_time)
            if count > 0:
                print(f"  - {et.value}: {count:,} events hydrated")
                # Report memory after each ledger hydration
                try:
                    mem_after = process.memory_info().rss / 1024 / 1024
                    mem_delta = mem_after - mem_before
                    print(f"💾 MEMORY: After {et.value}: {mem_after:.1f}MB (Δ{mem_delta:+.1f}MB)")
                    mem_before = mem_after
                except Exception:
                    pass

        self._historical_data_loaded = True
        
        # Final memory report
        try:
            mem_final = process.memory_info().rss / 1024 / 1024
            print(f"💾 MEMORY: After all hydration: {mem_final:.1f}MB")
        except Exception:
            pass
        
        print("📋 Ledger: Historical data loading completed")

    def cleanup_memory_ledgers(self, hours_to_keep: int = 25) -> None:
        """Clean up old events from memory ledgers."""
        cutoff_time = self.clock.now() - timedelta(hours=hours_to_keep)

        for ledger in self.ledgers.values():
            count = ledger.cleanup_memory(cutoff_time)
            if count > 0:
                self.logger.info(f"Cleaned {count} old events from {ledger.event_type.value} RAM cache")

    def start(self) -> bool:
        self._keep_running = True
        self._set_service_state(ServiceState.STARTING)
        for ledger in self.ledgers.values():
            ledger.start()
        self._system_ready = True
        self._set_service_state(ServiceState.RUNNING)
        return True

    def stop(self) -> bool:
        self._keep_running = False
        self._set_service_state(ServiceState.STOPPING)
        
        # Stop all ledgers in parallel to speed up shutdown
        threads = []
        print(f"📋 Ledger: Stopping {len(self.ledgers)} event-specific ledgers in parallel...")
        for et, ledger in self.ledgers.items():
            t = threading.Thread(target=ledger.stop, name=f"Stop-Ledger-{et.value}")
            t.start()
            threads.append(t)
            
        for t in threads:
            t.join(timeout=10.0)
            
        # Centrally shut down the notification executor if it's ours
        if self.notification_executor:
            try:
                if hasattr(self.notification_executor, "shutdown"):
                    print("📋 Ledger: Shutting down shared notification executor...")
                    self.notification_executor.shutdown(wait=True)
            except Exception as e:
                print(f"⚠️ Ledger: Error shutting down notification executor: {e}")

        self._set_service_state(ServiceState.STOPPED)
        return super().stop()

    def set_system_ready(self):
        self._system_ready = True
        for et, data, ts in self._buffered_events:
            self.ledgers[et].write_event(data, ts)
        self._buffered_events.clear()

    def get_event_counts(self) -> Dict[str, int]:
        counts = {}
        for et, ledger in self.ledgers.items():
            if ledger.ledger_file.exists():
                with open(ledger.ledger_file, "r") as f:
                    counts[et.name] = sum(1 for line in f if line.strip())
            else:
                counts[et.name] = 0
        return counts

    def health_check(self) -> Dict[str, Any]:
        ledger_stats = {}
        memory_stats = {}
        memory_enabled = []

        for et, ledger in self.ledgers.items():
            l_hc = ledger.health_check()
            ledger_stats[et.value] = l_hc
            memory_stats[str(et)] = l_hc.get("sequence_counter", 0) if ledger._is_memory_enabled else 0
            if ledger._is_memory_enabled:
                memory_enabled.append(et)

        return {
            "status": "healthy" if self._started and self._keep_running else "stopped",
            "ledger_dir": str(self.ledger_dir),
            "ledger_exists": self.ledger_dir.exists(),
            "system_ready": self._system_ready,
            "historical_data_loaded": self._historical_data_loaded,
            "memory_ledgers_enabled": memory_enabled,
            "memory_ledger_counts": memory_stats,
            "ledgers": ledger_stats,
        }
