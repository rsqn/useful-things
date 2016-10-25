package tech.rsqn.useful.things.interchange;

@FunctionalInterface
public interface Subscriber<T> {
    void on(T v);
}
