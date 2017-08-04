package tech.rsqn.search.proxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mandrewes on 8/6/17.
 */
public class SearchQuery {
    private List<SearchAttribute> attributes;
    private int limit;
//    private int pageSize;
//    private String lastKey;

    public SearchQuery() {
        attributes = new ArrayList<>();
        limit = 10;
    }

    public <T> SearchQuery with(String n, T v) {
        return and(n,v);
    }

    public <T> SearchQuery and(String n, T v) {
        if ( v instanceof Long ) {
            attributes.add(new SearchAttribute().with(n,v).andMatchType(SearchAttribute.Type.EQ));
        } else {
            attributes.add(new SearchAttribute().with(n,v.toString()).andMatchType(SearchAttribute.Type.FUZZY));
        }
        return this;
    }

    public SearchQuery limit(int n) {
        this.limit = n;
        return this;
    }

    public List<SearchAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<SearchAttribute> attributes) {
        this.attributes = attributes;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
