/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;

import java.io.Serializable;


/**
 * Author: mandrewes
 * Date: 16/08/11
 *
 *
 * Ideally all cache entry values are serializable so that the value can be written to disk or replicated.
 *
 * Sometimes this is not the case and you want to just cache an object in memory for a short time. This class
 * allows the cache to THINK that an object is serializable so keeping it in memory, but replication and disk persistence will
 * silently fail.
 *
 * @author mandrewes
 */
public class NonSerializableWrapper implements Serializable {
    private Object value;

    public static NonSerializableWrapper with(Object o) {
        NonSerializableWrapper ret = new NonSerializableWrapper();
        ret.setValue(o);

        return ret;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
