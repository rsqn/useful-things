from dataclasses import dataclass, asdict
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional


class EventType(Enum):
    PRICE_UPDATE = "price_update"
    CANDLE_UPDATE = "candle_update"
    PORTFOLIO_UPDATE = "portfolio_update"
    TRADE_PLACEMENT = "trade_placement"
    TRADE_FILLS = "trade_fills"
    VIRTUAL_ORDERS = "virtual_orders"
    SYSTEM_EVENT = "system_event"
    MARKET_ANALYSIS = "market_analysis"
    COINBASE_ORDER_PLACEMENT = "coinbase_order_placement"
    COINBASE_ORDER_STATUS = "coinbase_order_status"
    COINBASE_FILL_HISTORY = "coinbase_fill_history"
    TRADING_DECISION = "trading_decision"
    TRADE_EXECUTION = "trade_execution"
    TRADE_MATCH = "trade_match"
    ORDER_BOOK_SNAPSHOT = "order_book_snapshot"
    MARKET_REGIME = "market_regime"
    SYSTEM_COMMAND = "system_command"

class AssetClass(Enum):
    CRYPTOCURRENCY = "cryptocurrency"
    EQUITY = "equity"
    FUTURE = "future"

@dataclass
class BaseEvent:
    event_type: EventType
    timestamp: datetime
    data: Dict[str, Any]
    event_id: Optional[int] = None

    def __post_init__(self):
        # Convert string event_type to Enum if needed
        if isinstance(self.event_type, str):
            try:
                self.event_type = EventType(self.event_type)
            except ValueError:
                # If not a valid enum value, keep as string or handle error
                pass
        
        # Ensure timestamp is datetime
        if isinstance(self.timestamp, str):
            try:
                self.timestamp = datetime.fromisoformat(self.timestamp.replace('Z', '+00:00'))
            except ValueError:
                pass

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for serialization."""
        return {
            "event_type": self.event_type.value if isinstance(self.event_type, Enum) else str(self.event_type),
            "timestamp": self.timestamp.isoformat() if isinstance(self.timestamp, datetime) else self.timestamp,
            "data": self.data,
            "event_id": self.event_id
        }

@dataclass
class PriceUpdateEvent:
    symbol: str
    asset_class: AssetClass
    price: float
    volume_24h: float
    timestamp: datetime

    def __post_init__(self):
        if self.price < 0 or self.volume_24h < 0:
            raise ValueError("Price and volume must be positive")


