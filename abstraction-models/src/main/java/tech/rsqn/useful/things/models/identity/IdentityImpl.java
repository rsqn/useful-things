package tech.rsqn.useful.things.models.identity;

import java.io.Serializable;
import java.util.Objects;

public class IdentityImpl implements Identity, Serializable {
    private static final long serialVersionUID = 1407513096944993120L;

    private String uid;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityImpl identity = (IdentityImpl) o;
        return Objects.equals(uid, identity.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uid);
    }

    @Override
    public String toString() {
        return "Identity{" +
                "uid='" + uid + '\'' +
                '}';
    }
}
