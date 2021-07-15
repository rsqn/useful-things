package com.isignthis.taskmanager;

import com.codahale.metrics.Timer;
import com.isignthis.cluster.Cluster;
import com.mac.eventbus.EventBus;
import com.mac.eventbus.EventBusCallBack;
import com.mac.libraries.metrics.MetricsCore;
import com.mac.platform.audit.AuditEvent;
import com.mac.platform.audit.AuditQueue;
import com.mac.transmogrify.persistence.Query;
import com.mac.transmogrify.persistence.leveltwo.Store;
import com.mac.utils.taskmanager.Task;
import com.mac.utils.taskmanager.TaskDescriptor;
import com.mac.utils.taskmanager.TaskExecutor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class PersistentTaskExecutor implements TaskExecutor, ApplicationContextAware {

    AuditQueue audit;

    public enum TaskStatus {
        PENDING, RUNNING, CANCELED, FAILED, COMPLETED
    }

    public enum TaskTags {
        LIMIT_ONE_PENDING, UNIQUE_PER_BUILD, REMOVE_ON_COMPLETED
    }

    final int DEFAULT_MAX_TASK_ALLOWED = 1000;
    private int maximumTasksAllowed = 1000;

    private Logger log = LoggerFactory.getLogger(getClass());
    private EventBus bus;
    private String topic;
    private Executor executor;
    private Store store;
    private ApplicationContext applicationContext;

    private Cluster cluster;

    private PersistentTaskManager taskManager;

    private String buildNumberFileName;
    private String buildVersionProperty;

    private boolean runReports = false;
    private final Map<Runnable, RunningTask> runningTasksStarted = new HashMap<>();

    private Date lastIsMasterLog = null;

    public void setStore(Store store) {
        this.store = store;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
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

    public void setTaskManager(PersistentTaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void setBuildNumberFileName(String buildNumberFileName) {
        this.buildNumberFileName = buildNumberFileName;
    }

    public void setBuildVersionProperty(String buildVersionProperty) {
        this.buildVersionProperty = buildVersionProperty;
    }

    public void setRunReports(boolean runReports) {
        this.runReports = runReports;
    }

    public void setMaximumTasksAllowed(int maximumTasksAllowed) {
        this.maximumTasksAllowed = maximumTasksAllowed;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void init() {
        log.info("PersistentTaskExecutor initialising on bus topic {}", topic);

        bus.addListener(topic, new EventBusCallBack<TaskDescriptor>() {
            @Override
            public void onEvent(String topic, TaskDescriptor event) {
                // Pass on to the task manager
                taskManager.saveTask(event);
            }
        });

        log.info("PersistentTaskExecutor read on {}", topic);

        if (runReports) {
            log.info("PersistentTaskExecutor will run reports");
        } else {
            log.info("PersistentTaskExecutor will not run reports");
        }

        log.info("PersistentTaskExecutor will fetch {} tasks from DB", maximumTasksAllowed);


        Runtime.getRuntime().addShutdownHook(new Thread(this::preDestroy));

    }

    private static Date lastLog = new Date();

    @Scheduled(fixedDelay = 1000 * 60 * 10) // Every 10 minutes
    private void reportStats() {
        if (runReports) {
            // Class based metrics
            MetricsCore.report();

            // Report currently running tasks
            synchronized (runningTasksStarted) {
                for (RunningTask runningTask : runningTasksStarted.values()) {
                    long runningTimeMs = new Date().getTime() - runningTask.getStarted().getTime();
                    log.info("Running Task {} (id: {}) started {} ({} millis ago)", runningTask.getName(),
                        runningTask.getId(), runningTask.getStarted(), runningTimeMs);
                }
            }
        } else {
            log.info("Not running reports");
        }
    }

    private void preDestroy() {
        log.info("preDestroy - reporting stats");
        reportStats();
    }

    @Scheduled(fixedDelay = 3000)
    public void maintainScheduledTasks() {
        // create an executionUUID that we can use in the logs to identify all the tasks executed by this iteration
        String executionUUID = java.util.UUID.randomUUID().toString();

        // check if i am the master for retrieving tasks to be run
        if (!cluster.isMaster()) {
            // don't want to log too much, log every 3 minutes
            if (lastLog == null || DateUtils.addMinutes(new Date(), -3).before(lastLog)) {
                log.debug(executionUUID + ". I am NOT cluster master - not checking pending tasks");
                lastLog = new Date();
            }

            return;
        }

        // This is the master, lets find any pending tasks that are past there run time

        //safeguard from misconfiguration
        int safe_maximumTasksAllowed = maximumTasksAllowed > 0 ? maximumTasksAllowed : DEFAULT_MAX_TASK_ALLOWED;
        if (safe_maximumTasksAllowed != maximumTasksAllowed) {
            log.info("PersistentTaskExecutor defaulted max tasks allowed to {} was {}", safe_maximumTasksAllowed,
                maximumTasksAllowed);
        }

        List<TaskDescriptor> taskDescriptors = store.query(
            TaskDescriptor.class,
            Query.withLimits(0, maximumTasksAllowed).and("status").isEqualTo(TaskStatus.PENDING.name())
                .and("runAfter").isLessThanOrEqualTo(new Date())

        );

        for (TaskDescriptor taskDescriptor : taskDescriptors) {
            try {
                runTask(taskDescriptor, executionUUID);
            } catch (Exception e) {
                log.error("Exception while running task " + taskDescriptor.getBeanOrClass(), e);

                // For debugging bad tasks - We log a report of the stats straight away.
                reportStats();

                // reset the task status
                taskDescriptor.setStartTime(null);
                taskDescriptor.setStatus(TaskStatus.PENDING.name());
                store.put(taskDescriptor);

                // stop running further task if there is an exception
                break;
            }
        }
    }

    private void sendAuditEvent(String msg){
        AuditEvent auditEvent = new AuditEvent().withEvent("task-failed-alert")
            .andField("message", msg);
        audit.submit(auditEvent);

    }

    private void runTask(TaskDescriptor taskDescriptor, String executionUUID) {

        if (taskDescriptor.isOmitLogs()) {
            log.info(executionUUID + ". I AM cluster master, running task=" + taskDescriptor.getBeanOrClass());
        } else {
            log.info(executionUUID + ". I AM cluster master, running task=" + taskDescriptor);
        }

        if (taskDescriptor.getStartTime() == null) {
            // set as running with start time
            taskDescriptor.setStartTime(new Date());
        }

        taskDescriptor.setStatus(TaskStatus.RUNNING.name());

        store.put(taskDescriptor);

        Task task;
        try {
            task = Task.fromDescriptor(applicationContext, taskDescriptor);
        } catch (Exception e) { // no poison pills
            taskDescriptor.setCompleteTime(new Date());
            store.put(taskDescriptor.withStatus(TaskStatus.FAILED.name()));
            String msg = executionUUID + ". Exception creating task from descriptor:" + taskDescriptor;
            log.error(msg, e);
            sendAuditEvent(msg);
            return;
        }

        task.setUid(taskDescriptor.getUid());
        task.setBus(bus);
        task.setTopic(topic);
        task.setStore(store);
        task.setApplicationContext(applicationContext);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                String taskName = "taskExecutor-" + task.getBeanOrClass();

                Timer.Context timerContext = null;
                if (runReports) {
                    synchronized (runningTasksStarted) {
                        runningTasksStarted.put(this, new RunningTask(taskName, task.getUid()));
                    }
                    timerContext = MetricsCore.timer(taskName).time();
                }


                try {
                    log.info(executionUUID + ". Running Task. uid=" + task.getUid() + ",bean=" + task.getBeanOrClass());
                    task.run();

                    taskDescriptor.setCompleteTime(new Date());

                    long timeTookMs =
                        taskDescriptor.getCompleteTime().getTime() - taskDescriptor.getStartTime().getTime();
                    log.info(executionUUID + ". Completed executing task. Time took ms=" + timeTookMs + ", uid=" +
                        task.getUid() + ",bean=" + task.getBeanOrClass());

                    if (taskDescriptor.getTags() != null &&
                        taskDescriptor.getTags().contains(TaskTags.REMOVE_ON_COMPLETED.name())) {
                        log.debug(executionUUID + ". Deleting Task " + ToStringBuilder.reflectionToString(task));
                        store.delete(taskDescriptor);
                    } else {
                        store.put(taskDescriptor.withStatus(TaskStatus.COMPLETED.name()));

                        log.info(
                            executionUUID + ". Finished Task. uid=" + task.getUid() + ",bean=" + task.getBeanOrClass());
                    }
                } catch (Exception e) {
                    taskDescriptor.setCompleteTime(new Date());
                    store.put(taskDescriptor.withStatus(TaskStatus.FAILED.name()));
                    log.error(executionUUID + ". Exception running task " + task, e);
                } finally {
                    if (runReports) {
                        if (timerContext != null) {
                            timerContext.close();
                        }
                        synchronized (runningTasksStarted) {
                            runningTasksStarted.remove(this);
                        }
                    }
                }
            }
        };

        executor.execute(runnable);
    }

    public void setAudit(AuditQueue audit) { this.audit = audit; }



    /**
     * Class to keep track of running tasks.
     */
    private class RunningTask {
        private final Date started = new Date();
        private final String name;
        private final String id;

        public RunningTask(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public Date getStarted() {
            return started;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }
    }
}