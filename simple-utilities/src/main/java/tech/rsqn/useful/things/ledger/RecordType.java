package tech.rsqn.useful.things.ledger;

import java.util.Objects;

/**
 * Record types supported by the ledger system.
 * This class wraps a string value to provide type safety while allowing arbitrary types.
 */
public final class RecordType {
    private final String value;

    public RecordType(String value) {
        this.value = Objects.requireNonNull(value, "RecordType value must not be null");
    }

    public String getValue() {
        return value;
    }

    public static RecordType of(String value) {
        return new RecordType(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordType that = (RecordType) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
