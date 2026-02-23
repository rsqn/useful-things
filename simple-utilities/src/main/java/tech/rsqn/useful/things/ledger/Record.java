package tech.rsqn.useful.things.ledger;

import java.time.Instant;
import java.util.Objects;

/**
 * Abstract base class for all ledger records.
 */
public abstract class Record {
    private RecordType type;
    private Instant timestamp;
    private Long sequenceId;
    private String eventId;

    protected Record() {
        this.timestamp = Instant.now();
    }

    public RecordType getType() {
        return type;
    }

    public void setType(RecordType type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Long getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(Long sequenceId) {
        this.sequenceId = sequenceId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record = (Record) o;
        return Objects.equals(type, record.type) &&
                Objects.equals(timestamp, record.timestamp) &&
                Objects.equals(sequenceId, record.sequenceId) &&
                Objects.equals(eventId, record.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, timestamp, sequenceId, eventId);
    }

    @Override
    public String toString() {
        return "Record{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", sequenceId=" + sequenceId +
                ", eventId='" + eventId + '\'' +
                '}';
    }
}
