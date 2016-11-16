package tech.rsqn.useful.things.concurrency;

public class NotifiableContainer {
    public String topic;
    public Notifiable callBack;

    public NotifiableContainer with(String t, Notifiable cb) {
        this.topic = t;
        this.callBack = cb;
        return this;
    }

}
