package com.mac.utils.taskmanager;

import com.mac.eventbus.ReferenceEventBus;
import org.testng.annotations.Test;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:41 AM
 * To change this template use File | Settings | File Templates.
 */
@Test(groups = {"unit-test"})
public class SampleEventBusTask extends Task<String> {

    @Override
    public void run() {
        ReferenceEventBus.gi().send(getConfig(),"Task has run");
    }
}
