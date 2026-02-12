"""
Individual ledger for a specific event type with dedicated thread pool.

Implements intelligent flushing:
- Dirty tracking to avoid unnecessary flushes
- Wall-clock time intervals (not simulation clock)
- Flush-before-read for consistency
- Bulk write mode for high-throughput operations
"""

import json
import logging
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any, Callable, Dict, Iterator, List, Optional

from .event_types import BaseEvent, EventType


class DecimalEncoder(json.JSONEncoder):
    """JSON encoder that handles Decimal types."""

    def default(self, obj):
        from decimal import Decimal

        if isinstance(obj, Decimal):
            return str(obj)  # Preserve precision as string
        return super().default(obj)


class EventLedger:
    """Individual ledger for a specific event type."""

    def __init__(
        self,
        event_type: EventType,
        ledger_file: Path,
        config,
        auto_flush: bool = True,
        notification_executor: Optional[ThreadPoolExecutor] = None,
        clock: Any = None,
    ):
        self.event_type = event_type
        self.ledger_file = ledger_file
        self.config = config
        self.auto_flush = auto_flush
        self.logger = logging.getLogger(f"EventLedger.{event_type.value}")
        self._sequence_counter = 0
        self._subscribers = []
        self._started = False
        self._keep_running = True
        self.clock = clock

        if self.clock is None:
            from utils.clock import RealTimeClock

            self.clock = RealTimeClock()

        # Memory cache support
        self._memory_cache: List[BaseEvent] = []
        self._is_memory_enabled = False
        self._memory_lock = threading.RLock()  # Re-entrant lock for thread safety

        # Intelligent flush tracking (Medium + Good fix)
        self._write_count_since_flush = 0
        self._last_flush_time = time.time()  # Use wall-clock, not simulation clock
        self._dirty = False  # Track if unflushed writes exist
        self._bulk_mode = False  # Suspend periodic flushing during bulk operations
        
        # Configurable flush intervals (can be overridden from config)
        self._flush_interval_writes = config.get_int("ledger.flush_interval_writes", 5000)
        self._flush_interval_seconds = float(config.get_decimal("ledger.flush_interval_seconds", Decimal("5.0")))
        self._flush_before_read = config.get_bool("ledger.flush_before_read", True)

        # Event-specific thread pool configuration
        if notification_executor:
            self.notification_executor = notification_executor
            print(f"🔧 LEDGER DEBUG: EventLedger for {event_type.value} using provided executor")
        else:
            pool_configs = {
                EventType.PRICE_UPDATE: 10,  # High frequency, allow parallel processing
                EventType.VIRTUAL_ORDERS: 4,  # Critical trading events
                EventType.TRADE_FILLS: 4,  # Critical trading events
                EventType.PORTFOLIO_UPDATE: 2,  # Sequential updates
                EventType.SYSTEM_EVENT: 2,  # System events
                EventType.MARKET_ANALYSIS: 2,  # Analysis events
            }

            max_workers = pool_configs.get(event_type, 2)
            self.notification_executor = ThreadPoolExecutor(
                max_workers=max_workers, thread_name_prefix=f"{event_type.value}-notify"
            )
            print(f"🔧 LEDGER DEBUG: Created EventLedger for {event_type.value} with {max_workers} workers")

        self._file_handle = None
        self._load_sequence_counter()

    def enable_memory_cache(self) -> None:
        """Enable in-memory storage for this ledger."""
        with self._memory_lock:
            self._is_memory_enabled = True
            if not self._memory_cache:
                self._memory_cache = []

    def disable_memory_cache(self) -> None:
        """Disable in-memory storage and clear cache."""
        with self._memory_lock:
            self._is_memory_enabled = False
            self._memory_cache = []

    def hydrate(self, cutoff_time: datetime) -> int:
        """Populate RAM cache from disk up to cutoff time using streaming methods."""
        if not self.ledger_file.exists():
            return 0

        count = 0

        try:
            # Clear existing cache BEFORE building new one to avoid memory spike
            # This prevents having both old and new lists in memory simultaneously
            with self._memory_lock:
                self._memory_cache = []
                self._is_memory_enabled = False

            # Report memory before hydration
            try:
                import psutil
                process = psutil.Process()
                mem_before = process.memory_info().rss / 1024 / 1024
            except Exception:
                mem_before = None

            # Use streaming method and append directly to cache to avoid intermediate list
            # This follows ledger-streaming-efficiency: use existing streaming infrastructure
            # Note: read_events_stream() will read from disk since _is_memory_enabled = False
            events_processed = 0
            for event in self.read_events_stream():
                if event.timestamp >= cutoff_time:
                    # Append with lock to ensure thread safety
                    with self._memory_lock:
                        self._memory_cache.append(event)
                    count += 1
                    events_processed += 1
                    
                    # Report memory every 50k events to track growth
                    if events_processed % 50000 == 0:
                        try:
                            mem_current = process.memory_info().rss / 1024 / 1024
                            if mem_before:
                                mem_delta = mem_current - mem_before
                                print(f"💾 MEMORY: {self.event_type.value} hydration: {events_processed:,} events, {mem_current:.1f}MB (Δ{mem_delta:+.1f}MB)")
                        except Exception:
                            pass

            if count > 0:
                with self._memory_lock:
                    self._is_memory_enabled = True

            # Final memory report for this ledger
            if mem_before and count > 0:
                try:
                    mem_after = process.memory_info().rss / 1024 / 1024
                    mem_delta = mem_after - mem_before
                    print(f"💾 MEMORY: {self.event_type.value} hydration complete: {count:,} events, {mem_after:.1f}MB (Δ{mem_delta:+.1f}MB)")
                except Exception:
                    pass

            return count

        except Exception as e:
            self.logger.error(f"Failed to hydrate {self.event_type.value}: {e}")
            return 0

    def cleanup_memory(self, cutoff_time: datetime) -> int:
        """Prune old events from RAM cache."""
        if not self._is_memory_enabled:
            return 0

        with self._memory_lock:
            original_count = len(self._memory_cache)
            self._memory_cache = [event for event in self._memory_cache if event.timestamp >= cutoff_time]
            cleaned_count = original_count - len(self._memory_cache)
            return cleaned_count

    def read_events(self, limit: Optional[int] = None) -> Iterator[BaseEvent]:
        """Read events from the ledger, using memory cache if available."""
        # Ensure consistency: flush pending writes before reading
        self._ensure_flushed()
        
        # Check memory cache first
        if self._is_memory_enabled:
            with self._memory_lock:
                count = 0
                for event in self._memory_cache:
                    if limit and count >= limit:
                        break
                    yield event
                    count += 1
            return

        # Fallback to disk
        if not self.ledger_file.exists():
            return

        try:
            with open(self.ledger_file, "r") as f:
                count = 0
                for line in f:
                    if limit and count >= limit:
                        break
                    try:
                        raw_data = json.loads(line.strip())
                        if "timestamp" in raw_data and isinstance(raw_data["timestamp"], str):
                            raw_data["timestamp"] = datetime.fromisoformat(
                                raw_data["timestamp"].replace("Z", "+00:00")
                            )

                        yield BaseEvent(**raw_data)
                        count += 1
                    except (json.JSONDecodeError, ValueError, TypeError):
                        continue
        except Exception as e:
            self.logger.error(f"Failed to read events from disk: {e}")

    def read_events_stream(self) -> Iterator[BaseEvent]:
        """Read events as BaseEvent objects for streaming processing, using memory if available."""
        # Ensure consistency: flush pending writes before reading
        self._ensure_flushed()
        
        if self._is_memory_enabled:
            with self._memory_lock:
                yield from self._memory_cache
            return

        if not self.ledger_file.exists():
            return

        try:
            with open(self.ledger_file, "r") as f:
                for line in f:
                    try:
                        raw_data = json.loads(line.strip())
                        if "timestamp" in raw_data and isinstance(raw_data["timestamp"], str):
                            raw_data["timestamp"] = datetime.fromisoformat(
                                raw_data["timestamp"].replace("Z", "+00:00")
                            )

                        yield BaseEvent(**raw_data)
                    except (json.JSONDecodeError, ValueError, TypeError):
                        continue
        except Exception as e:
            self.logger.error(f"Failed to stream events from disk: {e}")

    def read_events_stream_reverse(self) -> Iterator[BaseEvent]:
        """Read events as BaseEvent objects in reverse order, using memory if available."""
        # Ensure consistency: flush pending writes before reading
        self._ensure_flushed()
        
        if self._is_memory_enabled:
            with self._memory_lock:
                yield from reversed(self._memory_cache)
            return

        if not self.ledger_file.exists():
            return

        try:
            # Efficient reverse line reading using seek to avoid loading large files into memory
            with open(self.ledger_file, "rb") as f:
                f.seek(0, os.SEEK_END)
                pointer = f.tell()
                buffer = b""

                while pointer > 0:
                    to_read = min(65536, pointer)
                    pointer -= to_read
                    f.seek(pointer)
                    chunk = f.read(to_read)
                    buffer = chunk + buffer
                    lines = buffer.split(b"\n")
                    buffer = lines[0]

                    for i in range(len(lines) - 1, 0, -1):
                        line = lines[i]
                        if line.strip():
                            try:
                                raw_data = json.loads(line.decode("utf-8"))
                                if "timestamp" in raw_data and isinstance(raw_data["timestamp"], str):
                                    raw_data["timestamp"] = datetime.fromisoformat(
                                        raw_data["timestamp"].replace("Z", "+00:00")
                                    )
                                yield BaseEvent(**raw_data)
                            except (json.JSONDecodeError, UnicodeDecodeError, ValueError, TypeError):
                                continue

                if buffer.strip():
                    try:
                        raw_data = json.loads(buffer.decode("utf-8"))
                        if "timestamp" in raw_data and isinstance(raw_data["timestamp"], str):
                            raw_data["timestamp"] = datetime.fromisoformat(
                                raw_data["timestamp"].replace("Z", "+00:00")
                            )
                        yield BaseEvent(**raw_data)
                    except (json.JSONDecodeError, UnicodeDecodeError, ValueError, TypeError):
                        pass
        except Exception as e:
            self.logger.error(f"Failed to reverse stream events from disk: {e}")

    def read_events_filtered(self, filter_func: Callable[[Dict[str, Any]], bool]) -> Iterator[BaseEvent]:
        """Read filtered events from the ledger as a stream."""
        # Return stream directly instead of converting to list to avoid memory copy
        yield from self.stream_events_filtered(filter_func)

    def stream_events_filtered(self, filter_func: Callable[[Dict[str, Any]], bool]) -> Iterator[BaseEvent]:
        """Stream events with custom filter function."""
        # Ensure consistency: flush pending writes before reading
        self._ensure_flushed()
        
        if self._is_memory_enabled:
            with self._memory_lock:
                for ev in self._memory_cache:
                    # Create lightweight dict view for filter function to avoid full to_dict() copy
                    # This creates a dict but reuses the same data dict reference
                    event_dict = {
                        "event_type": ev.event_type.value if isinstance(ev.event_type, EventType) else str(ev.event_type),
                        "timestamp": ev.timestamp.isoformat() if isinstance(ev.timestamp, datetime) else ev.timestamp,
                        "data": ev.data,  # Reuse same dict reference
                        "event_id": ev.event_id
                    }
                    if filter_func(event_dict):
                        yield ev
            return

        # Fallback to disk
        if not self.ledger_file.exists():
            return

        try:
            with open(self.ledger_file, "r") as f:
                for line in f:
                    if line.strip():
                        try:
                            raw_data = json.loads(line.strip())
                            if filter_func(raw_data):
                                if "timestamp" in raw_data and isinstance(raw_data["timestamp"], str):
                                    raw_data["timestamp"] = datetime.fromisoformat(
                                        raw_data["timestamp"].replace("Z", "+00:00")
                                    )
                                yield BaseEvent(**raw_data)
                        except (json.JSONDecodeError, ValueError, TypeError):
                            continue
        except Exception as e:
            self.logger.error(f"Failed to stream filtered events from disk: {e}")

    def get_latest_event(self) -> Optional[BaseEvent]:
        """Get the most recent event."""
        if self._is_memory_enabled:
            with self._memory_lock:
                return self._memory_cache[-1] if self._memory_cache else None

        # Fallback to reverse stream
        try:
            for event in self.read_events_stream_reverse():
                return event
            return None
        except Exception as e:
            self.logger.error(f"Failed to get latest event: {e}")
            return None

    def _load_sequence_counter(self):
        """Load sequence counter from this ledger's file using an efficient tail scan."""
        if not self.ledger_file.exists():
            self._sequence_counter = 0
            return

        try:
            # Fast path: Read last few lines to find the highest event_id
            file_size = self.ledger_file.stat().st_size
            if file_size == 0:
                self._sequence_counter = 0
                return

            with open(self.ledger_file, "rb") as f:
                # Seek to near the end of the file (last 4KB should be enough for many lines)
                seek_pos = max(0, file_size - 4096)
                f.seek(seek_pos)
                tail_data = f.read().decode("utf-8", errors="ignore")
                lines = tail_data.strip().split("\n")

                # Check lines from newest to oldest
                for line in reversed(lines):
                    if not line.strip():
                        continue

                    # Fast string extraction for "event_id": N
                    id_idx = line.find('"event_id": ')
                    if id_idx != -1:
                        try:
                            start = id_idx + 12
                            end = line.find(",", start)
                            if end == -1:
                                end = line.find("}", start)
                            val_str = line[start:end].strip()
                            if val_str.isdigit():
                                self._sequence_counter = int(val_str)
                                print(
                                    f"🔧 LEDGER DEBUG: {self.event_type.value} loaded max_id={self._sequence_counter} (tail scan)"
                                )
                                return
                        except Exception as e:
                            self.logger.debug(
                                "EventLedger %s: tail-scan parse error (continuing): %s",
                                self.event_type.value,
                                e,
                            )
                            continue

            # Fallback: If no ID found in the tail, do a full sequential scan (should be rare)
            print(f"⚠️ LEDGER DEBUG: Tail scan failed for {self.event_type.value}, falling back to sequential scan...")
            max_id = 0
            with open(self.ledger_file, "r") as f:
                for line in f:
                    id_idx = line.find('"event_id": ')
                    if id_idx != -1:
                        try:
                            start = id_idx + 12
                            end = line.find(",", start)
                            if end == -1:
                                end = line.find("}", start)
                            val_str = line[start:end].strip()
                            if val_str.isdigit():
                                max_id = max(max_id, int(val_str))
                        except Exception as e:
                            self.logger.debug(
                                "EventLedger %s: sequential-scan parse error (continuing): %s",
                                self.event_type.value,
                                e,
                            )
                            continue
            self._sequence_counter = max_id
            print(f"✅ LEDGER DEBUG: {self.event_type.value} loaded max_id={max_id} (sequential fallback)")

        except Exception as e:
            self.logger.exception("Error reading %s: %s", self.ledger_file.name, e)
            raise

    def start(self):
        """Start the event ledger."""
        try:
            # Open file handle in append mode
            self._file_handle = open(self.ledger_file, "a")
            self._started = True
            print(f"✅ EventLedger: {self.event_type.value} ready (file handle open, auto_flush={self.auto_flush})")
        except Exception as e:
            print(f"❌ EventLedger {self.event_type.value}: Failed to open file handle: {e}")
            self.logger.error(f"Failed to open file handle: {e}")
            self._started = False

    def stop(self):
        """Stop the event ledger."""
        self._keep_running = False

        # CRITICAL: Flush any pending writes before closing
        if hasattr(self, "_file_handle") and self._file_handle:
            try:
                pending = getattr(self, "_write_count_since_flush", 0)
                self._file_handle.flush()
                try:
                    import os

                    if hasattr(self._file_handle, "fileno"):
                        os.fsync(self._file_handle.fileno())
                except (AttributeError, OSError):
                    pass

                if pending > 0:
                    print(f"🔍 DEBUG: Final flush of {self.event_type.value} ledger ({pending} pending writes)")

                self._file_handle.close()
                self._file_handle = None
            except Exception as e:
                print(f"❌ EventLedger {self.event_type.value}: Error closing file: {e}")
                self.logger.error(f"Error closing file: {e}")

        # DO NOT shutdown notification_executor here if it's likely shared
        # LedgerManager will handle central shutdown of the shared executor.
        # We only shut down if we created it (indicated by absence of provided executor in __init__)
        # However, to be safe and consistent with centralized shutdown, we skip it here.
        
        print(f"✅ EventLedger: {self.event_type.value} stopped")

    def force_flush(self):
        """Force flush any pending writes to disk."""
        if hasattr(self, "_file_handle") and self._file_handle and self._dirty:
            try:
                pending = self._write_count_since_flush
                self._file_handle.flush()
                self._write_count_since_flush = 0
                self._last_flush_time = time.time()  # Wall-clock time
                self._dirty = False
                if pending > 0:
                    self.logger.debug(f"Force flushed {self.event_type.value} ({pending} pending writes)")
                return True
            except Exception as e:
                self.logger.error(f"Failed to force flush {self.event_type.value}: {e}")
                return False
        return False
    
    def _ensure_flushed(self):
        """Ensure all pending writes are flushed before reading (consistency)."""
        if self._dirty and self._flush_before_read:
            self.force_flush()
    
    @contextmanager
    def bulk_write_mode(self):
        """Context manager for bulk write operations - suspends periodic flushing.
        
        Usage:
            with ledger.bulk_write_mode():
                for event in massive_dataset:
                    ledger.write_event(...)
            # Auto-flush happens on exit
        """
        old_bulk_mode = self._bulk_mode
        self._bulk_mode = True
        try:
            yield
        finally:
            self._bulk_mode = old_bulk_mode
            if not old_bulk_mode:
                # Flush when exiting bulk mode (unless we were nested)
                self.force_flush()

    def write_event(self, data: Dict[str, Any], timestamp: datetime = None) -> int:
        """Write event to this ledger."""
        if not self._keep_running:
            return -1

        # Get next sequence ID
        self._sequence_counter += 1
        event_id = self._sequence_counter

        try:
            # Use provided timestamp or timestamp from data if available, otherwise current time
            if timestamp:
                event_timestamp = timestamp
            elif "timestamp" in data and data["timestamp"]:
                if isinstance(data["timestamp"], str):
                    event_timestamp = datetime.fromisoformat(data["timestamp"].replace("Z", "+00:00"))
                elif isinstance(data["timestamp"], datetime):
                    event_timestamp = data["timestamp"]
                else:
                    event_timestamp = self.clock.now()
            else:
                event_timestamp = self.clock.now()

            # Create a copy of data for serialization, converting datetime objects to strings
            serializable_data = {}
            for key, value in data.items():
                if isinstance(value, datetime):
                    serializable_data[key] = value.isoformat()
                else:
                    serializable_data[key] = value

            # Create event object for internal use and subscribers
            event_obj = BaseEvent(
                event_type=self.event_type, timestamp=event_timestamp, data=serializable_data, event_id=event_id
            )

            # Update memory cache if enabled
            if self._is_memory_enabled:
                with self._memory_lock:
                    self._memory_cache.append(event_obj)

            # Create event record as raw dictionary for JSON serialization
            event_dict = {
                "event_type": self.event_type.value,
                "timestamp": event_timestamp.isoformat(),
                "data": serializable_data,
                "event_id": event_id,
            }

            # Write to file using open handle
            if self._started and hasattr(self, "_file_handle") and self._file_handle:
                self._file_handle.write(json.dumps(event_dict, cls=DecimalEncoder) + "\n")
                self._dirty = True
                self._write_count_since_flush += 1

                if self.auto_flush:
                    self._file_handle.flush()
                    self._dirty = False
                    self._write_count_since_flush = 0
                elif not self._bulk_mode:
                    # Periodic flush using wall-clock time (not simulation clock)
                    current_time = time.time()
                    if (
                        self._write_count_since_flush >= self._flush_interval_writes
                        or current_time - self._last_flush_time >= self._flush_interval_seconds
                    ):
                        try:
                            self._file_handle.flush()
                            self._write_count_since_flush = 0
                            self._last_flush_time = current_time
                            self._dirty = False
                        except Exception as e:
                            self.logger.warning(f"Failed to flush {self.event_type.value} ledger: {e}")
                # In bulk_mode, skip periodic flushing entirely - will flush on exit
            else:
                # Fallback if not started
                with open(self.ledger_file, "a") as f:
                    f.write(json.dumps(event_dict, cls=DecimalEncoder) + "\n")
                    f.flush()

            # Notify subscribers
            self._notify_subscribers(event_obj)

            return event_id

        except Exception as e:
            self.logger.error(f"Failed to write event: {e}")
            return -1

    def subscribe(self, callback: Callable[[BaseEvent], None]) -> None:
        """Subscribe to this specific event type."""
        # Get subscriber info for logging
        callback_name = getattr(callback, "__self__", {})
        if hasattr(callback_name, "__class__"):
            subscriber_name = callback_name.__class__.__name__
        else:
            subscriber_name = str(callback.__name__ if hasattr(callback, "__name__") else "unknown")

        subscriber = {
            "callback": callback,
            "last_processed_id": self._sequence_counter,
            "subscriber_name": subscriber_name,
        }
        self._subscribers.append(subscriber)

        print(
            f"📡 Ledger: {subscriber_name} subscribed to {self.event_type.value} events (starting from ID {self._sequence_counter})"
        )

    def _notify_subscribers(self, event: BaseEvent) -> None:
        """Notify all subscribers of a new event."""
        if not self._subscribers:
            return

        # Check if executor is shutdown
        if getattr(self.notification_executor, "_shutdown", False):
            self._do_notify_sync(event)
            return

        try:
            self.notification_executor.submit(self._do_notify_sync, event)
        except RuntimeError:
            # Fallback to sync if thread pool is full or shutdown
            self._do_notify_sync(event)

    def _do_notify_sync(self, event: BaseEvent) -> None:
        """Execute subscriber notifications synchronously."""
        event_id = event.event_id
        is_noisy = self.event_type in [EventType.PRICE_UPDATE, EventType.CANDLE_UPDATE]

        for subscriber in self._subscribers:
            if event_id > subscriber["last_processed_id"]:
                try:
                    subscriber["callback"](event)
                    subscriber["last_processed_id"] = event_id
                except Exception as e:
                    subscriber_name = subscriber.get("subscriber_name", "unknown")
                    self.logger.error(f"Subscriber {subscriber_name} error: {e}", exc_info=True)

    def health_check(self) -> Dict[str, Any]:
        """Check health of the event ledger."""
        queue_size = -1
        if hasattr(self.notification_executor, "_work_queue"):
            queue_size = self.notification_executor._work_queue.qsize()

        return {
            "status": "healthy" if self._started and self._keep_running else "stopped",
            "event_type": self.event_type.value,
            "sequence_counter": self._sequence_counter,
            "subscriber_count": len(self._subscribers),
            "notification_queue_size": queue_size,
        }
