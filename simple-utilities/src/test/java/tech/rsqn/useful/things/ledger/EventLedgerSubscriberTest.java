package tech.rsqn.useful.things.ledger;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class EventLedgerSubscriberTest extends EventLedgerTestBase {

    @Test
    public void testSubscribe() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        Consumer<BaseEvent> subscriber = Mockito.mock(Consumer.class);
        ledger.subscribe(subscriber);

        ledger.writeEvent(createData("val", 1), Instant.now());

        // Verify subscriber called (sync or async depending on executor)
        // Default executor is cached thread pool, so async.
        // But we need to wait.
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.any(BaseEvent.class));
    }

    @Test
    public void testSubscribeAsync() throws IOException, InterruptedException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        CountDownLatch latch = new CountDownLatch(1);
        ledger.subscribe(event -> latch.countDown());

        ledger.writeEvent(createData("val", 1), Instant.now());

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testFilteredSubscribe() throws IOException {
        ledger = new EventLedger(EventType.PRICE_UPDATE, ledgerFile, config);
        ledger.start();

        Consumer<BaseEvent> subscriber = Mockito.mock(Consumer.class);
        
        // Subscribe with filter: only accept events where data.val > 10
        ledger.subscribe(subscriber, event -> {
            Number val = (Number) event.getData().get("val");
            return val != null && val.intValue() > 10;
        });

        // Write event that should be filtered OUT
        ledger.writeEvent(createData("val", 5), Instant.now());
        
        // Write event that should be ACCEPTED
        ledger.writeEvent(createData("val", 15), Instant.now());

        // Verify subscriber called only once (for the second event)
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.argThat(event -> {
            Number val = (Number) event.getData().get("val");
            return val != null && val.intValue() == 15;
        }));
    }
}
