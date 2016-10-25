package tech.rsqn.useful.things.concurrency;

public class Latch {

    public void unlatch() {
        try {
            synchronized (this) {
                this.notifyAll();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void waitFor() {
        this.waitFor(-1);
    }

    public void waitFor(long ms) {
        try {
            synchronized (this) {
                if (ms > 0) {
                    this.wait(ms);
                } else {
                    this.wait();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
