package tech.rsqn.useful.things.ledger;

import java.io.IOException;
import java.math.BigDecimal;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Gson adapter that writes {@link BigDecimal} as plain decimal JSON numbers
 * (never scientific notation from {@link BigDecimal#toString()}).
 */
public final class BigDecimalPlainTypeAdapter extends TypeAdapter<BigDecimal> {

    @Override
    public void write(JsonWriter out, BigDecimal value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        // jsonValue emits a raw number token; toPlainString avoids 1E-8 style forms.
        out.jsonValue(value.toPlainString());
    }

    @Override
    public BigDecimal read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) {
            return new BigDecimal(in.nextString());
        }
        throw new IOException("Expected BigDecimal number or string, got " + token);
    }
}
