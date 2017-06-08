package tech.rsqn.search.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by mandrewes on 5/6/17.
 */
public class IndexEntry {
    private String uid;
    private Map<String,String> attrs;
    private String reference;

    public IndexEntry() {
        attrs = new HashMap<>();
        uid = UUID.randomUUID().toString();
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Map<String, String> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, String> attrs) {
        this.attrs = attrs;
    }

    public void putAttr(String key, String v) {
        this.attrs.put(key,v);
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
