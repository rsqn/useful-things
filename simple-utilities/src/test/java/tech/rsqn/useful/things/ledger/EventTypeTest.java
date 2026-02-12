package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

public class EventTypeTest {

    @Test
    public void testFromValue() {
        Assert.assertEquals(EventType.fromValue("price_update"), EventType.PRICE_UPDATE);
        Assert.assertEquals(EventType.fromValue("system_event"), EventType.SYSTEM_EVENT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUnknownValue() {
        EventType.fromValue("unknown_event_type");
    }

    @Test
    public void testGetValue() {
        Assert.assertEquals(EventType.PRICE_UPDATE.getValue(), "price_update");
    }
}
