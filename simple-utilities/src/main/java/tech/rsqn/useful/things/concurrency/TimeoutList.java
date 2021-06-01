package tech.rsqn.useful.things.concurrency;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 10/27/13
 * Time: 11:07 AM
 *
 * This is a poor implementation of what I want here, come back and fix it later..
 */
public class TimeoutList<VT> {
    private List<TimeoutListEntry<VT>> values = new ArrayList<>();

    public void add(VT v, long ttl) {
        TimeoutListEntry<VT> entry = new TimeoutListEntry<>();
        entry.setExpireTime(System.currentTimeMillis() + ttl);
        entry.setValue(v);
        synchronized (values) {
            sweep();
            values.add(entry);
        }
    }

    public int size() {
        return values.size();
    }

    public VT get(int i) {
        synchronized (values) {
            sweep();
            TimeoutListEntry<VT> entry = values.get(i);
            if (entry != null) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean contains(VT v) {
        TimeoutListEntry<VT> entry = new TimeoutListEntry<>();
        entry.setValue(v);
        synchronized (values) {
            sweep();
            return values.contains(entry);
        }
    }

    public void remove(VT v) {
        TimeoutListEntry<VT> entry = new TimeoutListEntry<>();
        entry.setValue(v);

        synchronized (values) {
            values.remove(entry);
        }
    }

    private void sweep() {
        Iterator<TimeoutListEntry<VT>> it = values.iterator();
        TimeoutListEntry<VT> entry;

        while (it.hasNext()) {
            entry  = it.next();
            if ( entry.isExpired()) {
                it.remove();
            }
        }

    }

}
