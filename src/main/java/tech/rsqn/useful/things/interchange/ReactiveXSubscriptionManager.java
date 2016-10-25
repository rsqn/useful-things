package tech.rsqn.useful.things.interchange;

public class ReactiveXSubscriptionManager extends SubscriptionManager {

    public Subscription subscribeToTopic(Subscriber subscriber) {
        Subscription ret = new ReactiveXSubscription();


    }
}
