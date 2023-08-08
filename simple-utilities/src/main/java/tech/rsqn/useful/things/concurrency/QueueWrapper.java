package tech.rsqn.useful.things.concurrency;


import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.rsqn.useful.things.metrics.Metrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class QueueWrapper<T> {
    private static final Logger LOG = LoggerFactory.getLogger(QueueWrapper.class);
    private String id;
    private BlockingQueue q;
    private volatile boolean keepRunning = false;
    private Counter enqueueCtr;
    private Counter dequeueCtr;
    private Counter notifyCtr;
    private Counter errorsCtr;
    private Timer notifyTimer;

    public QueueWrapper(String id) {
        this.keepRunning = true;
        this.id = id;
        this.q = new ArrayBlockingQueue(50000, true); // normally 2000. pushed it up to 50 to do max speed tests and queue can buffer on refresh or resort.

        enqueueCtr = Metrics.counter(getClass(), id + "-EQ");
        dequeueCtr = Metrics.counter(getClass(), id + "-DQ");
        errorsCtr = Metrics.counter(getClass(), id + "-ERR");
        notifyCtr = Metrics.counter(getClass(), id + "-notifyCtr");
        notifyTimer = Metrics.timer(getClass(), id + "-notifyTmr");
    }

    public void enqueue(T v) {
        q.add(v);
        enqueueCtr.inc();
    }

    public void cancel() {
        keepRunning = false;
        q.clear();
    }

    public T deQueue(long waitMs) {
        T ret = null;
        try {
            ret = (T) q.poll(waitMs, TimeUnit.MILLISECONDS);
            if (ret != null) {
                dequeueCtr.inc();
            }
        } catch (InterruptedException e) {
            errorsCtr.inc();
            LOG.warn(e.getMessage(), e);
        }
        return ret;
    }

    public void listen(QueueListener<T> l) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    LOG.info("QueueListener {} starting",id);
                    while (keepRunning) {
                        T item = deQueue(5000);
                        if (item != null && keepRunning) {
                            Timer.Context ctx = notifyTimer.time();
                            try {
                                l.onItem(item);
                                notifyCtr.inc();
                            } catch (Exception e) {
                                LOG.warn("Error notifying queue listener " + id + " " + e.getMessage(), e);
                                errorsCtr.inc();
                            } finally {
                                ctx.stop();
                            }
                        }
                    }
                    LOG.info("QueueListener {} exiting",id);
                } catch (Exception e) {
                    errorsCtr.inc();
                    LOG.warn("Exception in queueWrapper " + id + " " + e.getMessage(), e);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }
}
