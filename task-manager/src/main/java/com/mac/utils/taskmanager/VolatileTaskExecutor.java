package com.mac.utils.taskmanager;

import com.isignthis.cluster.Cluster;
import com.mac.eventbus.EventBus;
import com.mac.eventbus.EventBusCallBack;
import com.mac.transmogrify.persistence.leveltwo.Store;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class VolatileTaskExecutor implements TaskExecutor, ApplicationContextAware {

    private Logger log = LoggerFactory.getLogger(getClass());
    private EventBus bus;
    private String topic;
    private Executor executor;
    private Store store;
    private List<TaskDescriptor> scheduledTasks = new ArrayList();
    private ApplicationContext applicationContext;
    private Cluster cluster;

    public void setStore(Store store) {
        this.store = store;
    }

    public void setBus(EventBus bus) {
        this.bus = bus;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
//        Task.appCtxHack = applicationContext;
    }

    public void init() {
        log.info("VolatileTaskExecutor initialising on bus topic {}", topic);

        bus.addListener(topic, new EventBusCallBack<TaskDescriptor>() {
            @Override
            public void onEvent(String topic, TaskDescriptor event) {
                onDescriptorReceived(event);
            }
        });
        log.info("VolatileTaskExecutor read on {}", topic);
    }

    private void scheduleFutureTask(TaskDescriptor desc) {
        log.info("Scheduling future task " + desc);
        synchronized (scheduledTasks) {
            scheduledTasks.add(desc);
        }
    }

    public boolean isGridMaster() {
        if (cluster != null) {
            if (cluster.isMaster()) {
                return true;
            }
        }

        return false;
    }

    @Scheduled(fixedDelay = 1000)
    public void maintainScheduledTasks() {

        // Important: if there is no grid, it will still execute
        if (cluster != null) {
            if (cluster.isMaster()) {
                return;
            }
        }

        synchronized (scheduledTasks) {
            Iterator<TaskDescriptor> it = scheduledTasks.iterator();
            TaskDescriptor descriptor;
            while (it.hasNext()) {
                descriptor = it.next();
                if ( descriptor.getRunAfter().getTime() < System.currentTimeMillis()) {
                    it.remove();
                    runTask(Task.fromDescriptor(applicationContext,descriptor));
                }
            }
        }
    }

    public void runTask(final Task task) {
        task.setBus(bus);
        task.setTopic(topic);
        task.setStore(store);
        task.setApplicationContext(applicationContext);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    log.debug("Running Task " + ToStringBuilder.reflectionToString(task));
                    task.run();
                    log.debug("Completed Task " + ToStringBuilder.reflectionToString(task));
                } catch (Exception e) {
                    log.warn("Exception running task " + task, e);
                }
            }
        });
    }


    public void onDescriptorReceived(TaskDescriptor desc) {
        log.info("Descriptor received class = {}", desc.getBeanOrClass());
        if(!desc.isOmitLogs()){
            log.info("Descriptor received config = {}", desc.getConfiguration());
        }
        try {
            if (desc.getRunAfter() != null && desc.getRunAfter().getTime() > System.currentTimeMillis()) {
                scheduleFutureTask(desc);
            } else {
                Task task = Task.fromDescriptor(applicationContext,desc);
                runTask(task);
            }
        } catch (Exception e) {
            log.error("Exception initialising/running task " + e, e);
        }
    }


}
