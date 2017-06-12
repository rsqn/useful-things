package tech.rsqn.search.proxy;

import java.util.Map;

/**
 * Created by mandrewees on 12/6/17.
 */
public class IndexMetrics {

    private Map<String,String> data;

    public IndexMetrics() {
    }

    public void put(String k, Object v) {
        data.put(k,v.toString());
    }

    public String get(String k) {
        return data.get(k);
    }

    @Override
    public String toString() {
        return "IndexMetrics{" +
                "data=" + data +
                '}';
    }
}
