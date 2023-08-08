package tech.rsqn.useful.things.concurrency;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

public class Notifier {
    private List<NotifiableContainer> listeners;
    private ExecutorService executorService;
    private Logger LOG = LoggerFactory.getLogger(getClass());
    private BlockingQueue taskQueue;
    private String name = "default";

//    private Counter requestsCtr;
//    private Counter completedCtr;
//    private Counter errorsCtr;
//    private Timer callingTimer;
//    private Timer submittingTimer;
//    private Timer toCompletionTimer;

    public Notifier() {
        this("default", 1, 2, 1000, 1000);
    }

    public Notifier(String name) {
        this(name, 1, 2, 1000, 1000);
    }

    public Notifier(String name, int minThreads, int maxThreads, int timeoutMs, int taskQueueSize) {
        this.name = name;
        listeners = new ArrayList<>();
        taskQueue = new ArrayBlockingQueue(taskQueueSize);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(minThreads, maxThreads, timeoutMs, TimeUnit.MILLISECONDS, taskQueue);
        executor.allowCoreThreadTimeOut(true);
        this.executorService = executor;
//        requestsCtr = Metrics.counter(getClass(), name + "-requests");
//        completedCtr = Metrics.counter(getClass(), name + "-completed");
//        errorsCtr = Metrics.counter(getClass(), name + "-errors");
//        callingTimer = Metrics.timer(getClass(), name + "-calling");
//        submittingTimer = Metrics.timer(getClass(), name + "-submitting");
//        toCompletionTimer = Metrics.timer(getClass(), name + "-completion");
    }

    public void setName(String name) {
        this.name = name;
    }

    public <T> void listen(String topic, Notifiable<T> l) {
        synchronized (listeners) {
            listeners.add(new NotifiableContainer().with(topic, l));
        }
    }

    public void remove(Comparable c) {
        synchronized (listeners) {
            Iterator it = listeners.iterator();
            Object o;
            while (it.hasNext()) {
                o = it.next();
                if (c.compareTo(o) == 0) {
                    it.remove();
                }
            }
        }
    }

    public void removeAllListeners() {
        synchronized (listeners) {
            listeners.clear();
        }
    }

    public void shutdown() {
        try {
            LOG.info("Notifier shutting down");
            executorService.shutdown();
            while (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.info("Awaiting completion of threads.");
                executorService.shutdownNow();
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        LOG.info("Notifier Shutdown");
    }

    public void send(String topic, final Object arg) {
        if (executorService.isShutdown()) {
            return;
        }
//        requestsCtr.inc();
//        Timer.Context sc = submittingTimer.time();

        try {
            listeners.forEach((listener) -> {
                if (listener.topic.equals(topic) || listener.topic.equals("*")) {
//                    Timer.Context tc = toCompletionTimer.time();
                    try {
                        executorService.submit(new Runnable() {
                            @Override
                            public void run() {
//                                Timer.Context c = callingTimer.time();
                                try {

                                    listener.callBack.onNotify(arg);
//                                    completedCtr.inc();
                                } catch (Exception e) {
                                    LOG.warn("Exception  [" + name + "] notifying listeners on topic (" + topic + ") with argument (" + arg + ")", e);
//                                    errorsCtr.inc();
                                } finally {
//                                    c.close();
//                                    tc.close();
                                }

                            }
                        });
                    } catch (Exception e) {
                        LOG.warn("Exception  [" + name + "] queueing task " + e.getMessage());
//                        errorsCtr.inc();
                        throw new NotifierException("Exception Queueing task ", e);
                    }
                }
            });
        } finally {
//            sc.close();
        }
    }
}