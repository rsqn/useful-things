package tech.rsqn.useful.things.interchange;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.interchange.reactivex.ReactiveXSubscriptionManager;

import java.util.concurrent.atomic.AtomicLong;

public class ReactiveXSubscriptionTest {

    SubscriptionManager mgr;

    @BeforeMethod
    public void setUp() throws Exception {
        mgr = new ReactiveXSubscriptionManager();
    }

    @Test
    public void shouldPushPop() throws Exception {
        final AtomicLong l = new AtomicLong();
        mgr.subscribeToQueue(ev -> {
            System.out.println(ev);
            l.incrementAndGet();
        });

        mgr.push("butter");

        Assert.assertEquals(l.get(), 1);
    }

    @Test
    public void shouldPubToTenSubscribers() throws Exception {
        final AtomicLong l = new AtomicLong();

        for (int i = 0; i < 10; i++) {
            final int c = i;
            mgr.subscribeToQueue(ev -> {
                System.out.println(c + " - " + ev);
                l.incrementAndGet();
            });
        }
        mgr.push("butter");

        Assert.assertEquals(l.get(), 10);
    }


    // to big for travisCI
//    @Test
//    public void shouldPubToOneHundredThousandThenUnsbuscribeToFiftyThousandSubscribers() throws Exception {
//        final AtomicLong l = new AtomicLong();
//        final List<Subscription> subs = new ArrayList<>();
//
//        for (int i = 0; i < 100 * 1000; i++) {
//            Subscription s = mgr.subscribeToQueue(ev -> {
//                l.incrementAndGet();
//            });
//            subs.add(s);
//        }
//        mgr.push("butter");
//        Assert.assertEquals(l.get(), 100 * 1000);
//
//        for (int i = 0; i < subs.size() / 2; i++) {
//            mgr.unsubscribe(subs.get(i));
//        }
//
//        mgr.push("butterX");
//        Assert.assertEquals(l.get(), 150 * 1000);
//    }
}
