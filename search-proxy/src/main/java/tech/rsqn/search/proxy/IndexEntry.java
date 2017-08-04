package tech.rsqn.search.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IndexEntry {
    private String uid;
    private Map<String, IndexAttribute> attrs;
    private String reference;

    public IndexEntry() {
        attrs = new HashMap<>();
        uid = UUID.randomUUID().toString();
        setUid(uid);
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
        addAttr("id", uid);
    }

    public Map<String, IndexAttribute> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, IndexAttribute> attrs) {
        this.attrs = attrs;
    }

    public void addAttr(String key, String v) {
        this.attrs.put(key, new IndexAttribute().with(Attribute.Type.String, v));
    }

    public void addTextAttr(String key, String v) {
        this.attrs.put(key, new IndexAttribute().with(Attribute.Type.Text, v));
    }

    public void addAttr(String key, Long v) {
        this.attrs.put(key, new IndexAttribute().with(Attribute.Type.Long, v));
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
        addAttr("reference",reference);
    }
}
