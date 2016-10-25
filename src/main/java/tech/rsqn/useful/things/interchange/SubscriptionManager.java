package tech.rsqn.useful.things.interchange;

public abstract class SubscriptionManager {

    public abstract <T> Subscription subscribeToQueue(Subscriber<T> subscriber);

    public abstract void unsubscribe(Subscription subscription);

    public abstract <T> void push(T v);
}
