package tech.rsqn.useful.things.ledger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event data class.
 */
public final class BaseEvent {
    private final EventType eventType;
    private final Instant timestamp;
    private final Map<String, Object> data;
    private final Long eventId;

    public BaseEvent(EventType eventType, Instant timestamp, Map<String, Object> data, Long eventId) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(data));
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Long getEventId() {
        return eventId;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("event_type", eventType.getValue());
        map.put("timestamp", timestamp.toString());
        map.put("data", data);
        if (eventId != null) {
            map.put("event_id", eventId);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static BaseEvent fromMap(Map<String, Object> map) {
        String typeStr = (String) map.get("event_type");
        EventType type = EventType.fromValue(typeStr);

        Object tsObj = map.get("timestamp");
        Instant ts;
        if (tsObj instanceof String) {
            ts = Instant.parse((String) tsObj);
        } else {
            throw new IllegalArgumentException("Timestamp must be a string in ISO-8601 format");
        }

        Map<String, Object> data = (Map<String, Object>) map.get("data");
        
        Long eventId = null;
        Object idObj = map.get("event_id");
        if (idObj instanceof Number) {
            eventId = ((Number) idObj).longValue();
        }

        return new BaseEvent(type, ts, data, eventId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEvent baseEvent = (BaseEvent) o;
        return eventType == baseEvent.eventType &&
                Objects.equals(timestamp, baseEvent.timestamp) &&
                Objects.equals(data, baseEvent.data) &&
                Objects.equals(eventId, baseEvent.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, timestamp, data, eventId);
    }

    @Override
    public String toString() {
        return "BaseEvent{" +
                "eventType=" + eventType +
                ", timestamp=" + timestamp +
                ", data=" + data +
                ", eventId=" + eventId +
                '}';
    }
}
