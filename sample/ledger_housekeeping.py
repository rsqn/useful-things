#!/usr/bin/env python3
"""Ledger housekeeping functions"""

import time
from datetime import datetime, timedelta, timezone

from ledger.event_types import EventType


def cleanup_old_price_data(ledger_manager, hours_to_keep=25):
    """Remove price data older than specified hours from in-memory ledger"""

    event_type = EventType.PRICE_UPDATE

    # Only clean if in-memory ledger is enabled
    if event_type not in ledger_manager._memory_enabled:
        return 0

    cutoff_time = ledger_manager.clock.now() - timedelta(hours=hours_to_keep)

    with ledger_manager._memory_lock:
        memory_events = ledger_manager._memory_ledgers.get(event_type, [])
        original_count = len(memory_events)

        if original_count == 0:
            return 0

        # Keep only events newer than cutoff
        filtered_events = []
        for event in memory_events:
            try:
                # Parse timestamp from event
                event_time = datetime.fromisoformat(event.timestamp.replace("Z", "+00:00"))
                if event_time >= cutoff_time:
                    filtered_events.append(event)
            except Exception:
                # Keep events with unparseable timestamps (safer)
                filtered_events.append(event)

        # Replace the memory ledger with filtered events
        ledger_manager._memory_ledgers[event_type] = filtered_events

        removed_count = original_count - len(filtered_events)

        if removed_count > 0:
            print(f"🧹 Housekeeping: Removed {removed_count:,} old price events (kept {len(filtered_events):,})")

        return removed_count


def flush_all_ledgers(ledger_manager):
    """Force flush all ledgers to ensure data persistence"""
    flushed_count = 0
    for event_type, ledger in ledger_manager.ledgers.items():
        if hasattr(ledger, "_file_handle") and ledger._file_handle and not ledger.auto_flush:
            try:
                # Check if there are pending writes
                pending_writes = getattr(ledger, "_write_count_since_flush", 0)
                if pending_writes > 0:
                    # Use the ledger's force_flush method if available, otherwise flush directly
                    if hasattr(ledger, "force_flush"):
                        if ledger.force_flush():
                            flushed_count += 1
                    else:
                        ledger._file_handle.flush()
                        ledger._write_count_since_flush = 0
                        ledger._last_flush_time = ledger_manager.clock.now().timestamp()
                        flushed_count += 1
            except Exception as e:
                print(f"⚠️ Housekeeping: Could not flush {event_type.value}: {e}")
    return flushed_count


def start_housekeeping_thread(ledger_manager, interval_minutes=30):
    """Start background thread to periodically clean old data and flush ledgers"""
    import threading

    def housekeeping_loop():
        flush_interval = 60  # Flush every 60 seconds
        cleanup_interval = interval_minutes * 60  # Cleanup every N minutes
        last_flush = ledger_manager.clock.now().timestamp()
        last_cleanup = ledger_manager.clock.now().timestamp()

        while True:
            try:
                current_time = ledger_manager.clock.now().timestamp()

                # Periodic flush for non-auto-flush ledgers (every 60 seconds)
                if current_time - last_flush >= flush_interval:
                    flushed = flush_all_ledgers(ledger_manager)
                    if flushed > 0:
                        print(f"🧹 Housekeeping: Flushed {flushed} ledgers with pending writes")
                    last_flush = current_time

                # Periodic cleanup (every N minutes)
                if current_time - last_cleanup >= cleanup_interval:
                    cleanup_old_price_data(ledger_manager)
                    last_cleanup = current_time

                # Sleep for 30 seconds and check again
                ledger_manager.clock.sleep(30)

            except Exception as e:
                print(f"❌ Housekeeping error: {e}")
                ledger_manager.clock.sleep(60)  # Wait longer on error

    thread = threading.Thread(target=housekeeping_loop, daemon=True)
    thread.start()
    print(f"🧹 Started housekeeping thread (flushes every 60s, cleanup every {interval_minutes} minutes)")
    return thread
