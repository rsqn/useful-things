package tech.rsqn.useful.things.concurrency;

@FunctionalInterface
public interface Notifiable<T> {
    void onNotify(T arg);
}
