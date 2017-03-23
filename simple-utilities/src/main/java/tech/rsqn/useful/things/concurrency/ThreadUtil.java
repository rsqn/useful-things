package tech.rsqn.useful.things.concurrency;

public class ThreadUtil {
    public static void doSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {

        }
    }

}
