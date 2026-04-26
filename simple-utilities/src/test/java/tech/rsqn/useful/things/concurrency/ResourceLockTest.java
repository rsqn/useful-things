package tech.rsqn.useful.things.concurrency;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

public class ResourceLockTest {

    @Test
    public void sameKeyIsSerialized() throws Exception {
        ResourceLock lock = new ResourceLock();
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(2);

        Runnable task = () -> {
            boolean ok = lock.tryWithLock("res1", Duration.ofSeconds(10), Duration.ofSeconds(5), () -> {
                int start = counter.get();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.set(start + 1);
            });
            Assert.assertTrue(ok);
            done.countDown();
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();

        Assert.assertTrue(done.await(3, TimeUnit.SECONDS));
        Assert.assertEquals(counter.get(), 2);
    }

    @Test
    public void differentKeysAreParallel() throws Exception {
        ResourceLock lock = new ResourceLock();

        CountDownLatch t1Inside = new CountDownLatch(1);
        CountDownLatch t2Done = new CountDownLatch(1);
        AtomicBoolean t2Succeeded = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            boolean ok = lock.tryWithLock("res1", Duration.ofSeconds(2), Duration.ofSeconds(5), () -> {
                t1Inside.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Assert.assertTrue(ok);
        });

        Thread t2 = new Thread(() -> {
            try {
                Assert.assertTrue(t1Inside.await(1, TimeUnit.SECONDS));
                boolean ok = lock.tryWithLock("res2", Duration.ofMillis(200), Duration.ofSeconds(5), () -> t2Succeeded.set(true));
                Assert.assertTrue(ok);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                t2Done.countDown();
            }
        });

        t1.start();
        t2.start();

        Assert.assertTrue(t2Done.await(1, TimeUnit.SECONDS));
        Assert.assertTrue(t2Succeeded.get());
        t1.join();
    }

    @Test
    public void releasesOnException() {
        ResourceLock lock = new ResourceLock();

        Assert.assertThrows(RuntimeException.class, () ->
                lock.tryWithLock("res1", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> {
                    throw new RuntimeException("boom");
                })
        );

        AtomicBoolean ran = new AtomicBoolean(false);
        Assert.assertTrue(lock.tryWithLock("res1", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> ran.set(true)));
        Assert.assertTrue(ran.get());
    }

    @Test
    public void nonReentrantAcquireFails() {
        ResourceLock lock = new ResourceLock();

        LockHandle h1 = lock.tryAcquire("res1", Duration.ofMillis(50), Duration.ofSeconds(5));
        Assert.assertNotNull(h1);
        try {
            LockHandle h2 = lock.tryAcquire("res1", Duration.ofMillis(50), Duration.ofSeconds(5));
            Assert.assertNull(h2);
        } finally {
            lock.release(h1);
        }
    }

    @Test
    public void tryAcquireTimesOutWhenHeld() throws Exception {
        ResourceLock lock = new ResourceLock();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            LockHandle h = lock.tryAcquire("res1", Duration.ofSeconds(1), Duration.ofSeconds(5));
            Assert.assertNotNull(h);
            acquired.countDown();
            try {
                Assert.assertTrue(release.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release(h);
            }
        });
        holder.start();

        Assert.assertTrue(acquired.await(1, TimeUnit.SECONDS));
        LockHandle contender = lock.tryAcquire("res1", Duration.ofMillis(50), Duration.ofSeconds(5));
        Assert.assertNull(contender);

        release.countDown();
        holder.join();
    }

    @Test
    public void leaseExpiryAllowsAcquireAfterRelease() throws Exception {
        ResourceLock lock = new ResourceLock();
        String key = "lease-key";

        CountDownLatch released = new CountDownLatch(1);
        AtomicReference<LockHandle> oldHandle = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            LockHandle h = lock.tryAcquire(key, Duration.ofMillis(50), Duration.ofMillis(50));
            Assert.assertNotNull(h);
            oldHandle.set(h);
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release(h);
                released.countDown();
            }
        });
        holder.start();

        Assert.assertTrue(released.await(2, TimeUnit.SECONDS));
        holder.join();

        LockHandle h2 = lock.tryAcquire(key, Duration.ofMillis(200), Duration.ofSeconds(5));
        Assert.assertNotNull(h2);
        try {
            Assert.assertNotEquals(h2.getFencingToken(), oldHandle.get().getFencingToken());
        } finally {
            lock.release(h2);
        }
    }

    @Test
    public void staleHandleReleaseIsIgnored() throws Exception {
        ResourceLock lock = new ResourceLock();
        String key = "stale-key";

        CountDownLatch released = new CountDownLatch(1);
        AtomicReference<LockHandle> stale = new AtomicReference<>();

        Thread holder = new Thread(() -> {
            LockHandle h = lock.tryAcquire(key, Duration.ofMillis(50), Duration.ofMillis(50));
            Assert.assertNotNull(h);
            stale.set(h);
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release(h);
                released.countDown();
            }
        });
        holder.start();

        Assert.assertTrue(released.await(2, TimeUnit.SECONDS));
        holder.join();

        LockHandle fresh = lock.tryAcquire(key, Duration.ofMillis(200), Duration.ofSeconds(5));
        Assert.assertNotNull(fresh);
        try {
            // Old handle should not unlock the fresh acquisition.
            lock.release(stale.get());
            LockHandle contender = lock.tryAcquire(key, Duration.ofMillis(50), Duration.ofSeconds(5));
            Assert.assertNull(contender);
        } finally {
            lock.release(fresh);
        }
    }

    @Test
    public void releaseFromWrongThreadIsIgnored() {
        ResourceLock lock = new ResourceLock();
        LockHandle h = lock.tryAcquire("wrong-thread", Duration.ofMillis(50), Duration.ofSeconds(5));
        Assert.assertNotNull(h);

        Thread other = new Thread(() -> lock.release(h));
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Still held by acquiring thread.
        LockHandle h2 = lock.tryAcquire("wrong-thread", Duration.ofMillis(50), Duration.ofSeconds(5));
        Assert.assertNull(h2);

        lock.release(h);
    }

    @Test
    public void tryAcquireInterruptedReturnsNullAndPreservesInterrupt() throws Exception {
        ResourceLock lock = new ResourceLock();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch contenderRunning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            LockHandle h = lock.tryAcquire("interrupt-key", Duration.ofSeconds(1), Duration.ofSeconds(5));
            Assert.assertNotNull(h);
            acquired.countDown();
            try {
                Assert.assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.release(h);
            }
        });
        holder.start();
        Assert.assertTrue(acquired.await(2, TimeUnit.SECONDS));

        AtomicBoolean interruptedAfterAcquire = new AtomicBoolean(false);
        Thread contender = new Thread(() -> {
            contenderRunning.countDown();
            LockHandle h = lock.tryAcquire("interrupt-key", Duration.ofSeconds(10), Duration.ofSeconds(5));
            interruptedAfterAcquire.set(Thread.currentThread().isInterrupted());
            if (h != null) {
                lock.release(h);
            }
        });
        contender.start();

        Assert.assertTrue(contenderRunning.await(2, TimeUnit.SECONDS));
        contender.interrupt();
        contender.join();

        Assert.assertNull(lock.tryAcquire("interrupt-key", Duration.ofMillis(50), Duration.ofSeconds(5)));
        Assert.assertTrue(interruptedAfterAcquire.get());

        release.countDown();
        holder.join();
    }

    @Test
    public void invalidLeaseThrows() {
        ResourceLock lock = new ResourceLock();
        Assert.assertThrows(IllegalArgumentException.class, () ->
                lock.tryAcquire("k", Duration.ofMillis(1), Duration.ZERO)
        );
    }

    @Test
    public void invalidWaitThrows() {
        ResourceLock lock = new ResourceLock();
        Assert.assertThrows(IllegalArgumentException.class, () ->
                lock.tryAcquire("k", Duration.ofMillis(-1), Duration.ofSeconds(1))
        );
    }

    @Test
    public void nullArgsThrowNpe() {
        ResourceLock lock = new ResourceLock();
        Assert.assertThrows(NullPointerException.class, () -> lock.tryAcquire(null, Duration.ofMillis(1), Duration.ofSeconds(1)));
        Assert.assertThrows(NullPointerException.class, () -> lock.tryAcquire("k", null, Duration.ofSeconds(1)));
        Assert.assertThrows(NullPointerException.class, () -> lock.tryAcquire("k", Duration.ofMillis(1), null));
        Assert.assertThrows(NullPointerException.class, () -> lock.tryWithLock("k", Duration.ofMillis(1), Duration.ofSeconds(1), (Runnable) null));
    }

    @Test
    public void uncontendedReleaseRemovesInternalTracking() {
        ResourceLock lock = new ResourceLock();
        String key = "cleanup-key";

        Assert.assertFalse(lock.isTrackedForTests(key));

        LockHandle h = lock.tryAcquire(key, Duration.ofMillis(50), Duration.ofSeconds(5));
        Assert.assertNotNull(h);
        Assert.assertTrue(lock.isTrackedForTests(key));

        lock.release(h);
        Assert.assertFalse(lock.isTrackedForTests(key));
    }
}

