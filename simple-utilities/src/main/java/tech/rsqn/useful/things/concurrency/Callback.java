package tech.rsqn.useful.things.concurrency;

@FunctionalInterface
public interface Callback<T> {
    void call(T arg);
}
