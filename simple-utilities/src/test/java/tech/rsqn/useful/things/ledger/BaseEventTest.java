package tech.rsqn.useful.things.ledger;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class BaseEventTest {

    @Test
    public void testConstructionAndGetters() {
        Instant now = Instant.now();
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        BaseEvent event = new BaseEvent(EventType.PRICE_UPDATE, now, data, 123L);

        Assert.assertEquals(event.getEventType(), EventType.PRICE_UPDATE);
        Assert.assertEquals(event.getTimestamp(), now);
        Assert.assertEquals(event.getData(), data);
        Assert.assertEquals(event.getEventId(), Long.valueOf(123L));
    }

    @Test
    public void testToMap() {
        Instant now = Instant.now();
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        BaseEvent event = new BaseEvent(EventType.PRICE_UPDATE, now, data, 123L);

        Map<String, Object> map = event.toMap();
        Assert.assertEquals(map.get("event_type"), "price_update");
        Assert.assertEquals(map.get("timestamp"), now.toString());
        Assert.assertEquals(map.get("data"), data);
        Assert.assertEquals(map.get("event_id"), 123L);
    }

    @Test
    public void testFromMap() {
        Instant now = Instant.now();
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        Map<String, Object> map = new HashMap<>();
        map.put("event_type", "price_update");
        map.put("timestamp", now.toString());
        map.put("data", data);
        map.put("event_id", 123L);

        BaseEvent event = BaseEvent.fromMap(map);
        Assert.assertEquals(event.getEventType(), EventType.PRICE_UPDATE);
        Assert.assertEquals(event.getTimestamp(), now);
        Assert.assertEquals(event.getData(), data);
        Assert.assertEquals(event.getEventId(), Long.valueOf(123L));
    }
}
