package tech.rsqn.useful.things.apps;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

public class KeepRunning {
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private Object lockObj_;
    private volatile boolean keepRunning_;
    private volatile boolean hasRunShutdownTasks_;
    private List<ShutdownHook> shutdownHooks_;

    public KeepRunning() {
        lockObj_ = new Object();
        keepRunning_ = true;
        hasRunShutdownTasks_ = false;
        shutdownHooks_ = new ArrayList<>();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                stopRunning();
            }
        });
    }

    public void addShutdownHook(String name, Callable c) {
        addShutdownHook(name, c, 50);
    }

    /*
     * Add a shutdown with Priority from 1 (Last to Stop) to 100 (First to Stop)
     *
     */
    public void addShutdownHook(String name, Callable c, Integer priority) {
        shutdownHooks_.add(new ShutdownHook(name, c, priority));
    }

    public void stopRunning() {
        this.keepRunning_ = false;
        try {
            synchronized (lockObj_) {
                lockObj_.notifyAll();
            }
        } catch (Exception ignore) {
            LOG.error("in keeprunning ", ignore);
        }
        runShutdownTasks();
    }

    private void runShutdownTasks() {
        synchronized (lockObj_) {
            if (hasRunShutdownTasks_ == true) {
                return;
            }
            hasRunShutdownTasks_ = true;
        }

        orderShutdownHooks();
        shutdownHooks_.forEach(hook -> {
            hook.call();
        });
    }

    private void orderShutdownHooks() {
        shutdownHooks_.sort(Comparator.comparing(ShutdownHook::getPriority).reversed());
    }

    public boolean shouldKeepRunning() {
        return keepRunning_;
    }

    public void doWait(long ms) {
        if (!keepRunning_) {
            return;
        }
        synchronized (lockObj_) {
            try {
                lockObj_.wait(ms);
            } catch (InterruptedException e) {
                LOG.error("in keeprunning ", e);
            }
        }
    }

    public void doNotify() {
        synchronized (lockObj_) {
            lockObj_.notifyAll();
        }
    }

}
