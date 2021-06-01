package tech.rsqn.useful.things.concurrency;

import tech.rsqn.useful.things.apps.KeepRunning;

public class Waiter {
    private Object lockObj = new Object();

    private KeepRunning keepRunning = new KeepRunning();

    public Waiter() {
        keepRunning.addShutdownHook("waiter",() -> {
            doNotify();
            return null;
        });
    }

    public void waitMs(long ms) {
        synchronized (lockObj) {
            try {
                lockObj.wait(ms);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public void doNotify() {
        synchronized (lockObj) {
            lockObj.notifyAll();
        }
    }

}
