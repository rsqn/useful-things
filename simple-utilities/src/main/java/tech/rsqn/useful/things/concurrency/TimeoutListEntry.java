package tech.rsqn.useful.things.concurrency;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 10/27/13
 * Time: 11:07 AM
 * To change this template use File | Settings | File Templates.
 */
public class TimeoutListEntry<T> {
    long expireTime;
    T value;

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeoutListEntry that = (TimeoutListEntry) o;

        if (!value.equals(that.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
