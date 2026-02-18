package tech.rsqn.useful.things.ledger;

import java.time.Instant;
import java.util.Objects;

/**
 * Abstract base class for all ledger records.
 */
public abstract class Record {
    private final RecordType type;
    private final Instant timestamp;
    private Long sequenceId;

    protected Record(RecordType type, Instant timestamp) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    protected Record(RecordType type, Instant timestamp, Long sequenceId) {
        this(type, timestamp);
        this.sequenceId = sequenceId;
    }

    public RecordType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Long getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(Long sequenceId) {
        this.sequenceId = sequenceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Record record = (Record) o;
        return Objects.equals(type, record.type) &&
                Objects.equals(timestamp, record.timestamp) &&
                Objects.equals(sequenceId, record.sequenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, timestamp, sequenceId);
    }

    @Override
    public String toString() {
        return "Record{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", sequenceId=" + sequenceId +
                '}';
    }
}
