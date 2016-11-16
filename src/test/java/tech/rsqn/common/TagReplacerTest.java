package tech.rsqn.common;

import org.testng.Assert;
import org.testng.annotations.Test;
import tech.rsqn.useful.things.util.TagReplacer;

import java.util.HashMap;
import java.util.Map;

public class TagReplacerTest {

    @Test
    public void shouldReplaceTags() throws Exception {
        String tpl = "path/{sessionId}";
        Map<String,String> tags = new HashMap<>();
        tags.put("sessionId","fred");

        String s = TagReplacer.replaceTags(tpl, tags);

        Assert.assertEquals("path/fred", s);

    }
}
