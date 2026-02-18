package tech.rsqn.useful.things.ledger;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class TestRecord extends Record {
    public static final RecordType TYPE = RecordType.of("test_record");
    
    private final String data;
    private final int value;

    public TestRecord(Instant timestamp, String data, int value) {
        super(TYPE, timestamp);
        this.data = data;
        this.value = value;
    }

    public String getData() {
        return data;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TestRecord that = (TestRecord) o;
        return value == that.value && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data, value);
    }

    @Override
    public String toString() {
        return "TestRecord{" +
                "data='" + data + '\'' +
                ", value=" + value +
                "} " + super.toString();
    }
}
