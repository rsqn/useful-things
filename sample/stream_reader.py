import json
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Iterator, Optional

from ledger.event_types import BaseEvent, EventType


class StreamReader:
    """Real-time event stream reader for the ledger system."""
    
    def __init__(self, ledger_manager):
        self.ledger_manager = ledger_manager
        self._subscribers: dict = {}
    
    def subscribe(self, event_type: EventType, callback: Callable[[dict], None]) -> None:
        """Subscribe to events of a specific type."""
        if event_type not in self._subscribers:
            self._subscribers[event_type] = []
        self._subscribers[event_type].append(callback)
    
    def unsubscribe(self, event_type: EventType, callback: Callable[[dict], None]) -> None:
        """Unsubscribe from events."""
        if event_type in self._subscribers:
            try:
                self._subscribers[event_type].remove(callback)
            except ValueError:
                pass
    
    def notify_subscribers(self, event: dict) -> None:
        """Notify all subscribers of a new event."""
        # handle dict or object with event_type attribute
        event_type = event.get("event_type") if isinstance(event, dict) else getattr(event, "event_type", None)
        if event_type and event_type in self._subscribers:
            for callback in self._subscribers[event_type]:
                try:
                    callback(event)
                except Exception:
                    pass  # Don't let subscriber errors break the system
    
    def tail_events(self, event_type: EventType, follow: bool = True) -> Iterator[dict]:
        """Tail events from a ledger file, optionally following new events."""
        ledger_file = self.ledger_manager._get_ledger_file(event_type)
        
        if not ledger_file.exists():
            return
            
        with open(ledger_file, 'r') as f:
            # Read existing events
            for line in f:
                try:
                    event_data = json.loads(line.strip())
                    yield event_data
                except json.JSONDecodeError:
                    continue
            
            # Follow new events if requested
            if follow:
                while True:
                    line = f.readline()
                    if line:
                        try:
                            event_data = json.loads(line.strip())
                            yield event_data
                        except json.JSONDecodeError:
                            continue
