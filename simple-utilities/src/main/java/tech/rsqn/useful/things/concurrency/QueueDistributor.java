package tech.rsqn.useful.things.concurrency;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class QueueDistributor<T> {
    private static final Logger LOG = LoggerFactory.getLogger(QueueDistributor.class);
    private Map<String, QueueWrapper<T>> subscriptions;

    public QueueDistributor() {
        subscriptions = new ConcurrentHashMap<>();
    }

    public void addQueue(String id, QueueWrapper<T> q, Function f) {
        synchronized (subscriptions) { //todo- remove this after sorting works reliably
            subscriptions.put(id, q);
            f.apply(null);
        }
    }

    public void removeQueue(String id) {
        synchronized (subscriptions) {
            if ( subscriptions.remove(id) != null ) {
                LOG.info("removed subscription " + id);
            } else {
                LOG.warn("subscription not found " + id);
            }
        }
    }

    public void enqueue(T ev) {
//        synchronized (subscriptions) {
            Collection<QueueWrapper<T>> list = subscriptions.values();
            for (QueueWrapper<T> tQueueWrapper : list) {
                tQueueWrapper.enqueue(ev);
            }
//        }
    }
}

