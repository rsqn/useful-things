package com.mac.utils.taskmanager;

import com.mac.eventbus.EventBus;
import com.mac.eventbus.EventBusCallBack;
import com.mac.eventbus.ReferenceEventBus;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:33 AM
 * To change this template use File | Settings | File Templates.
 */
@Test(groups = {"unit-test"})
public class VolatileTaskExecutorTest {

    EventBus bus;
    TaskManager tm;
    TaskExecutor executor;
    VolatileTaskExecutor vte;

    @BeforeMethod
    public void setUp() throws Exception {
        bus = ReferenceEventBus.gi();
        LocalTaskManager ltm = new LocalTaskManager();
        ltm.setBus(bus);
        ltm.setTopic("TEST");
        ltm.init();
        tm = ltm;

        vte = new VolatileTaskExecutor();
        vte.setTopic("TEST");
        vte.setBus(bus);
        vte.setExecutor(new ThreadPoolExecutor(2,5,1000, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10)));
        vte.init();
        executor = vte;
    }


    @Test
    public void shouldExecuteImmediateTask() throws Exception {
        SampleEventBusTask ebt = new SampleEventBusTask();
        ebt.setConfig("topic-" + System.currentTimeMillis());

        final List<String> received = new ArrayList<String>();
        bus.addListener(ebt.getConfig(), new EventBusCallBack<String>() {
            @Override
            public void onEvent(String topic, String event) {
                System.out.println("recieved");
                received.add(event);
            }
        });

        tm.submit(ebt);

        Thread.sleep(1000);

        Assert.assertEquals(received.size(),1);
    }

    @Test
    public void shouldScheduleFutureTask() throws Exception {
        SampleEventBusTask ebt = new SampleEventBusTask();
        ebt.setConfig("topic-" + System.currentTimeMillis());
        ebt.setRunAfter(new Date(System.currentTimeMillis() + 1000));

        final List<String> received = new ArrayList<String>();
        bus.addListener(ebt.getConfig(), new EventBusCallBack<String>() {
            @Override
            public void onEvent(String topic, String event) {
                System.out.println("recieved");
                received.add(event);
            }
        });

        tm.submit(ebt);

        Thread.sleep(1000);
        Assert.assertEquals(received.size(),0);

        vte.maintainScheduledTasks();
        Thread.sleep(1000);
        Assert.assertEquals(received.size(),1);
    }
}
