package tech.rsqn.useful.things.models.metadata;

public class Reference {
    private String uid;
    private String scope;
    private String sourceScope;

    private String sourceId;

    private String targetScope;
    private String targetId;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSourceScope() {
        return sourceScope;
    }

    public void setSourceScope(String sourceScope) {
        this.sourceScope = sourceScope;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public void setTargetScope(String targetScope) {
        this.targetScope = targetScope;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
}
