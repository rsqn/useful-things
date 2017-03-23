package tech.rsqn.useful.things.interchange.reactivex;

import tech.rsqn.useful.things.identifiers.UIDHelper;
import tech.rsqn.useful.things.interchange.Subscription;

public class ReactiveXSubscription extends Subscription {
    private String id;
    private long startTs;
    private rx.Subscription rxSubscription;

    public ReactiveXSubscription() {
        startTs = System.currentTimeMillis();
        id = UIDHelper.generate();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getStartTs() {
        return startTs;
    }

    public void setStartTs(long startTs) {
        this.startTs = startTs;
    }

    public rx.Subscription getRxSubscription() {
        return rxSubscription;
    }

    public void setRxSubscription(rx.Subscription rxSubscription) {
        this.rxSubscription = rxSubscription;
    }
}
