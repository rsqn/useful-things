package com.isignthis.taskmanager;

import com.isignthis.taskmanager.helper.BuildVersionHelper;
import com.mac.transmogrify.persistence.Query;
import com.mac.transmogrify.persistence.leveltwo.Store;
import com.mac.utils.taskmanager.Task;
import com.mac.utils.taskmanager.TaskDescriptor;
import com.mac.utils.taskmanager.TaskManager;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class PersistentTaskManager implements TaskManager {

    private static final Logger log = LoggerFactory.getLogger(PersistentTaskManager.class);

    private Store store;
    private String buildNumberFileName;
    private String buildVersionProperty;

    @Required
    public void setStore(Store store) {
        this.store = store;
    }

    @Required
    public void setBuildNumberFileName(String buildNumberFileName) {
        this.buildNumberFileName = buildNumberFileName;
    }

    @Required
    public void setBuildVersionProperty(String buildVersionProperty) {
        this.buildVersionProperty = buildVersionProperty;
    }

    @Override
    public void submit(Task t) {
        saveTask(t.getDescriptor());
    }

    @Override
    public void submit(Task t, Date d) {
        TaskDescriptor taskDescriptor = t.getDescriptor();
        taskDescriptor.setRunAfter(d);
        saveTask(taskDescriptor);
    }

    public void saveTask(TaskDescriptor task) {
        log.info("Descriptor received class = {}, config = {}", task.getBeanOrClass(), task.getConfiguration());
        AtomicBoolean setTask = new AtomicBoolean(true);

        if (task.getTags() != null) {
            if (task.getTags().contains(PersistentTaskExecutor.TaskTags.LIMIT_ONE_PENDING.name())) {
                String[] PENDING_STATUSES = {PersistentTaskExecutor.TaskStatus.PENDING.name(),
                    PersistentTaskExecutor.TaskStatus.RUNNING.name()};

                // find any pending or running tasks for this bean or class to decide if we should schedule received
                // task descriptor
                Query query =
                    Query.where("beanOrClass").isEqualTo(task.getBeanOrClass()).and("status").in(PENDING_STATUSES);

                store.query(TaskDescriptor.class, query, (TaskDescriptor t) -> {
                    // check if the scheduling task is the current task running, if so, its ok to schedule another
                    if (t.getStatus().equalsIgnoreCase(PersistentTaskExecutor.TaskStatus.RUNNING.name())) {
                        if (!StringUtils.hasLength(t.getScheduledFromUid())) {
                            log.debug("Allow existing task from boot to be re-scheduled. task=" +
                                ToStringBuilder.reflectionToString(t));
                            setTask.set(true);
                            return true;
                        } else if (t.getUid().equalsIgnoreCase(task.getScheduledFromUid())) {
                            log.debug("Allow existing running task from to be re-scheduled. task=" +
                                ToStringBuilder.reflectionToString(t));
                            setTask.set(true);
                            return true;
                        }
                    }

                    log.warn("Task is already scheduled to run or is running, ignore task="
                        + ToStringBuilder.reflectionToString(t));
                    setTask.set(false);
                    return false;
                });
            }

            if (task.getTags().contains(PersistentTaskExecutor.TaskTags.UNIQUE_PER_BUILD.name())) {
                String currentBuildVersion = BuildVersionHelper
                    .getCurrentBuildVersion(buildNumberFileName, buildVersionProperty);
                task.setBuildVersion(currentBuildVersion);
                Query query = Query.where("beanOrClass").isEqualTo(task.getBeanOrClass()).and("buildVersion")
                    .isEqualTo(currentBuildVersion);

                store.query(TaskDescriptor.class, query, (TaskDescriptor t) -> {
                    // We found a task that has already been started/scheduled for this current build
                    log.warn("Not running " + task.getBeanOrClass() +
                        " as it has already been scheduled before for this build (" + currentBuildVersion + ")");
                    setTask.set(false);
                    return false;
                });
            }
        }

        if (setTask.get()) {
            if (task.getRunAfter() == null) {
                // always set a runAfter date
                task.setRunAfter(new Date());
            }

            task.setStatus(PersistentTaskExecutor.TaskStatus.PENDING.name());
            store.put(task);
        }
    }
}
