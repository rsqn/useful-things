package tech.rsqn.useful.things.interchange.reactivex;

import rx.subjects.Subject;
import tech.rsqn.useful.things.interchange.Subscriber;
import tech.rsqn.useful.things.interchange.Subscription;
import tech.rsqn.useful.things.interchange.SubscriptionManager;

public class ReactiveXSubscriptionManager extends SubscriptionManager {
    private Subject singleSubject;

    public ReactiveXSubscriptionManager() {
        singleSubject = rx.subjects.PublishSubject.create();
    }

    public Subscription subscribeToQueue(Subscriber subscriber) {
        ReactiveXSubscription ret = new ReactiveXSubscription();
        rx.Subscription sub = singleSubject.subscribe(o -> subscriber.on(o));
        ret.setRxSubscription(sub);
        return ret;
    }

    public void unsubscribe(Subscription subscription) {
        ReactiveXSubscription rxs = (ReactiveXSubscription) subscription;
        rxs.getRxSubscription().unsubscribe();
    }

    public <T> void push(T v) {
        singleSubject.onNext(v);
    }
}
