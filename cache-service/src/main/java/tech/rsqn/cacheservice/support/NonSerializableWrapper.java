package tech.rsqn.cacheservice.support;

import java.io.Serializable;

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
