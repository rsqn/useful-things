package tech.rsqn.search.proxy;

public class IndexAttribute {
    private Attribute.Type attrType;
    private Object attrValue;

    public IndexAttribute with(Attribute.Type t, Object v) {
        this.attrType = t;
        this.attrValue = v;
        return this;
    }

    public Attribute.Type getAttrType() {
        return attrType;
    }

    public void setAttrType(Attribute.Type attrType) {
        this.attrType = attrType;
    }

    public <T> T getAttrValueAs(Class c) {
        return (T)attrValue;
    }

    public Object getAttrValue() {
        return attrValue;
    }

    public void setAttrValue(Object attrValue) {
        this.attrValue = attrValue;
    }
}
