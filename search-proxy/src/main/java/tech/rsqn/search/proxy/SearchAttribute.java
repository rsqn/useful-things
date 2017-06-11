package tech.rsqn.search.proxy;

/**
 * Created by mandrewes on 8/6/17.
 */
public class SearchAttribute {
    private String name;
    private String pattern;

    public static final String WILDCARD_FIELD = "*";

    public SearchAttribute with(String n, String p) {
        this.name = n;
        this.pattern = p;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

}
