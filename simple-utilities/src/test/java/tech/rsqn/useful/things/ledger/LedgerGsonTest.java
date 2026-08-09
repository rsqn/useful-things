package tech.rsqn.useful.things.ledger;

import java.math.BigDecimal;
import java.time.Instant;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.gson.Gson;

/**
 * Ledger Gson adapters: plain BigDecimal and null-safe Instant (optional fields).
 */
public class LedgerGsonTest {

    private final Gson gson = LedgerGson.create();

    static final class Holder {
        Instant receivedTime;
        BigDecimal price;
    }

    @Test
    public void instant_null_serializesWithoutNpe() {
        Holder h = new Holder();
        h.receivedTime = null;
        h.price = new BigDecimal("0.00000001");
        String json = gson.toJson(h);
        Assert.assertTrue(json.contains("0.00000001"), json);
        Holder round = gson.fromJson(json, Holder.class);
        Assert.assertNull(round.receivedTime);
        Assert.assertEquals(0, new BigDecimal("0.00000001").compareTo(round.price));
    }

    @Test
    public void instant_roundTrips() {
        Holder h = new Holder();
        h.receivedTime = Instant.parse("2026-08-09T12:00:00Z");
        String json = gson.toJson(h);
        Assert.assertEquals(gson.fromJson(json, Holder.class).receivedTime, h.receivedTime);
    }

    @Test
    public void instant_explicitNullToken_deserializesNull() {
        Holder h = gson.fromJson("{\"receivedTime\":null}", Holder.class);
        Assert.assertNull(h.receivedTime);
    }
}
