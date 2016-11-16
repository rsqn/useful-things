package tech.rsqn.useful.things.concurrency;

@FunctionalInterface
public interface QueueListener<T> {
    void onItem(T arg);
}
