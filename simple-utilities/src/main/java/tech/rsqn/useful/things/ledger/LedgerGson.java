package tech.rsqn.useful.things.ledger;

import java.math.BigDecimal;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * Shared Gson configuration for ledger JSONL persistence drivers.
 */
public final class LedgerGson {

    private LedgerGson() {
    }

    /**
     * @return Gson with RecordType, Instant, and plain BigDecimal adapters
     */
    public static Gson create() {
        return new GsonBuilder()
                .registerTypeAdapter(RecordType.class, new TypeAdapter<RecordType>() {
                    @Override
                    public void write(JsonWriter out, RecordType value) throws IOException {
                        out.value(value.getValue());
                    }

                    @Override
                    public RecordType read(JsonReader in) throws IOException {
                        return RecordType.of(in.nextString());
                    }
                })
                .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
                    @Override
                    public void write(JsonWriter out, Instant value) throws IOException {
                        // Null-safe: optional Instant fields (e.g. receivedTime on legacy rows)
                        if (value == null) {
                            out.nullValue();
                            return;
                        }
                        out.value(value.toString());
                    }

                    @Override
                    public Instant read(JsonReader in) throws IOException {
                        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                            in.nextNull();
                            return null;
                        }
                        return Instant.parse(in.nextString());
                    }
                })
                .registerTypeAdapter(BigDecimal.class, new BigDecimalPlainTypeAdapter())
                .create();
    }
}
