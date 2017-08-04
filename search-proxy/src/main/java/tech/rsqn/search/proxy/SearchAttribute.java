package tech.rsqn.search.proxy;

public class SearchAttribute {
    public enum Type {FUZZY,EQ};
    public static final String WILDCARD_FIELD = "*";

    private String name;
    private Object pattern;
    private Type matchType = Type.FUZZY;

    public <T> SearchAttribute with(String n, T p) {
        this.name = n;
        this.pattern = p;
        return this;
    }

    public SearchAttribute andMatchType(Type t) {
        this.matchType = t;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public <T> T getPattern() {
        return (T)pattern;
    }

    public <T> void setPattern(T pattern) {
        this.pattern = pattern;
    }

    public Type getMatchType() {
        return matchType;
    }

    public void setMatchType(Type matchType) {
        this.matchType = matchType;
    }
}
