package com.mac.utils.taskmanager;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mac.eventbus.EventBus;
import com.mac.transmogrify.persistence.leveltwo.Store;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:28 AM
 * To change this template use File | Settings | File Templates.
 */

public abstract class Task<T> {
    private String uid;
    private T config;
    private Map<String,String> context;
    protected Date runAfter;
    protected EventBus bus;
    protected String topic;
    protected Store store;
    private String beanOrClass = getClass().getName();
    private String buildVersion;
    protected ApplicationContext applicationContext = null;

    protected static Map<String, String> classMapping = new HashMap<>();

    public String getBeanOrClass() {
        return beanOrClass;
    }

    public void setBeanOrClass(String beanOrClass) {
        this.beanOrClass = beanOrClass;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setBus(EventBus bus) {
        this.bus = bus;
    }

    protected Task() {
        runAfter = new Date();
    }


    public TaskDescriptor getDescriptor() {
        TaskDescriptor ret = new TaskDescriptor();
        ret.setRequiredVersion("-1");
        ret.setBeanOrClass(getBeanOrClass());
        ret.setConfigClsName(getConfigType().getName());
        ret.setConfiguration(toConfigString(config));
        ret.setRunAfter(runAfter);
        return ret;
    }

    public static Task fromDescriptor(ApplicationContext ctx, TaskDescriptor descriptor) {
        try {
            Task ret = null;

            // bean should be prototype scope
            if (ctx == null) {
                ret = getTaskForName(descriptor);
            } else {
                if (ctx.containsBean(descriptor.getBeanOrClass())) {
                    ret = (Task) ctx.getBean(descriptor.getBeanOrClass());
                } else {
                    ret = (Task) ctx.getBean(Class.forName(descriptor.getBeanOrClass()));
                }
                if ( ret == null ) {
                    ret = getTaskForName(descriptor);
                }
            }

            ret.setConfig(fromConfigString(Class.forName(descriptor.getConfigClsName()), descriptor.getConfiguration()));

            // set context as well
            ret.setContext(descriptor.getContext());

            return ret;
        } catch (Exception e) {
            throw new RuntimeException("Exception creating task from descriptor " + descriptor, e);
        }
    }

    /*
     * Handle changes to package path or class name via a mapping
     */
    private static Task getTaskForName(TaskDescriptor descriptor)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return (Task) Class.forName(descriptor.getBeanOrClass()).newInstance();
        } catch(ClassNotFoundException e) {
            String newClassName = classMapping.getOrDefault(descriptor.getBeanOrClass(), null);
            if (null != newClassName && newClassName.length() > 0) {
                return (Task) Class.forName(newClassName).newInstance();
            }
            throw e;
        }
    }

    protected String toConfigString(T o) {
        if (o instanceof String) {
            return (String) o;
        }
        Gson builder = new GsonBuilder().setPrettyPrinting().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return builder.toJson(o);
    }

    protected static Object fromConfigString(Class c, String s) {
        if (String.class.isAssignableFrom(c)) {
            return s;
        }
        Gson builder = new GsonBuilder().setPrettyPrinting().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return builder.fromJson(s, c);
    }

    public T getConfig() {
        return config;
    }

    public void setConfig(T config) {
        this.config = config;
    }

    public Class getConfigType() {
        return config.getClass();
    }

    public Date getRunAfter() {
        return runAfter;
    }

    public void setRunAfter(Date runAfter) {
        this.runAfter = runAfter;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    public abstract void run();

    public Map<String, String> getClassMapping() {
        return Task.classMapping;
    }

    public void setClassMapping(Map<String, String> classNameMapping) {
        Task.classMapping = classNameMapping;
    }

    public void mergeClassMapping(Map<String, String> classNameMapping) {
        classNameMapping.forEach(new BiConsumer<String, String >() {
            @Override
            public void accept(String key, String value) {
                Task.classMapping.putIfAbsent(key, value);
            }
        });
    }
}
