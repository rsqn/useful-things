package tech.rsqn.useful.things.interchange;

import tech.rsqn.useful.things.identifiers.UIDHelper;

public class ReactiveXSubscription extends Subscription {
    private String id;
    private long startTs;

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
}
