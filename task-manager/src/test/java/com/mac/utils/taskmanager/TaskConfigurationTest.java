package com.mac.utils.taskmanager;

import com.mac.eventbus.EventBus;
import com.mac.eventbus.ReferenceEventBus;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User:
 * Date: 11/04/13
 * Time: 10:33 AM
 * To change this template use File | Settings | File Templates.
 */
@Test(groups = {"unit-test"})
public class TaskConfigurationTest {

    EventBus bus;

    @BeforeMethod
    public void setUp() throws Exception {
        bus = ReferenceEventBus.gi();
    }


    @Test
    public void shouldDeriveCorrectClassNameAndConfiguration() throws Exception {
        SampleTask task = new SampleTask();
        task.setConfig("boing");
        TaskDescriptor descriptor = task.getDescriptor();
        Assert.assertEquals(descriptor.getBeanOrClass(),"com.mac.utils.taskmanager.SampleTask");
        Assert.assertEquals(descriptor.getConfiguration(),"boing");
    }

    @Test
    public void shouldDeriveNewClassNameForOldClassNameAndConfiguration() throws Exception {
        SampleTask task = new SampleTask();
        task.setClassMapping(new HashMap<String, String>() {{
            put("com.mac.utils.taskmanager.deprecated.SampleTask", "com.mac.utils.taskmanager.SampleTask");
        }});
        task.setConfig("boing");
        TaskDescriptor descriptor = task.getDescriptor();
        Assert.assertEquals(descriptor.getBeanOrClass(),"com.mac.utils.taskmanager.SampleTask");
        Assert.assertEquals(descriptor.getConfiguration(),"boing");
    }
}
