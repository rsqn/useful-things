package tech.rsqn.useful.things.util;

import java.util.HashMap;
import java.util.Map;

public class TagReplacer {

    public static String replaceTags(String tpl, Map<String,String> tags) {
        String ret = tpl;
        for (String s : tags.keySet()) {
            ret = ret.replaceAll("\\{" + s + "\\}",tags.get(s));
        }
        return ret;
    }

    public static String replaceTags(String tpl, String... tags) {
        Map<String,String> m = new HashMap<>();

        if ( tags.length %2 != 0) {
            throw new RuntimeException("Tags list must be a multiple of 2. It is converted into a map");
        }

        for (int i = 0; i < tags.length ; i+=2) {
            m.put(tags[i],tags[i+1]);
        }
        return replaceTags(tpl,m);
    }
}
