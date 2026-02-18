package tech.rsqn.useful.things.ledger;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LedgerSubscriberTest extends LedgerTestBase {

    @Test
    public void testSubscribe() throws IOException {
        ledger = createLedger();

        Consumer<TestRecord> subscriber = Mockito.mock(Consumer.class);
        ledger.subscribe(null, subscriber);

        ledger.write(createRecord("val", 1));

        // Verify subscriber called (async)
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.any(TestRecord.class));
    }

    @Test
    public void testSubscribeAsync() throws IOException, InterruptedException {
        ledger = createLedger();

        CountDownLatch latch = new CountDownLatch(1);
        ledger.subscribe(null, event -> latch.countDown());

        ledger.write(createRecord("val", 1));

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testFilteredSubscribe() throws IOException {
        ledger = createLedger();

        Consumer<TestRecord> subscriber = Mockito.mock(Consumer.class);
        
        // Subscribe with filter: only accept events where value > 10
        ledger.subscribe(event -> event.getValue() > 10, subscriber);

        // Write event that should be filtered OUT
        ledger.write(createRecord("val", 5));
        
        // Write event that should be ACCEPTED
        ledger.write(createRecord("val", 15));

        // Verify subscriber called only once (for the second event)
        Mockito.verify(subscriber, Mockito.timeout(1000).times(1)).accept(Mockito.argThat(event -> event.getValue() == 15));
    }
}
