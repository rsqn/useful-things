package tech.rsqn.useful.things.authz.models;


import java.io.Serializable;

public class Identity implements Serializable {
    private static final long serialVersionUID = 1407513096944993121L;

    private String uid;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "Identity{" +
                "uid='" + uid + '\'' +
                '}';
    }
}
