package com.mac.utils.taskmanager;

import com.mac.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.Executor;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class LocalTaskManager implements TaskManager {
    private Logger log = LoggerFactory.getLogger(getClass());
    private EventBus bus;
    private String topic;

    public void setBus(EventBus bus) {
        this.bus = bus;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void init() {

    }

    @Override
    public void submit(Task t) {
        TaskDescriptor desc = t.getDescriptor();
        bus.send(topic,desc);
    }

    @Override
    public void submit(Task t, Date d) {
        TaskDescriptor desc = t.getDescriptor();
        desc.setRunAfter(d);
        bus.send(topic,desc);
    }
}
