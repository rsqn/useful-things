package com.mac.utils.taskmanager;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mac.transmogrify.model.TimestampedItem;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:18 AM
 * To change this template use File | Settings | File Templates.
 */
public class TaskDescriptor extends TimestampedItem {
    protected String scope;
    protected String beanOrClass;
    protected String buildVersion;
    protected String configClsName;
    protected String requiredVersion;
    protected String configuration;
    protected Date runAfter = new Date(); // default to execute straight away
    protected Date startTime;
    protected Date completeTime;
    protected String status;
    protected String scheduledFromUid;
    protected List<String> tags = new ArrayList<>();
    protected boolean omitLogs = true; // Prevent task executors from logging configuration
    protected Map<String,String> context = new HashMap<>(); // store any task context values

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getBeanOrClass() {
        return beanOrClass;
    }

    public void setBeanOrClass(String beanOrClass) {
        this.beanOrClass = beanOrClass;
    }

    public String getRequiredVersion() {
        return requiredVersion;
    }

    public void setRequiredVersion(String requiredVersion) {
        this.requiredVersion = requiredVersion;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public String getConfigClsName() {
        return configClsName;
    }

    public void setConfigClsName(String configClsName) {
        this.configClsName = configClsName;
    }

    public Date getRunAfter() {
        return runAfter;
    }

    public void setRunAfter(Date runAfter) {
        this.runAfter = runAfter;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public TaskDescriptor withStatus(String status) {
        this.status = status;
        return this;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(Date completeTime) {
        this.completeTime = completeTime;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getScheduledFromUid() {
        return scheduledFromUid;
    }

    public void setScheduledFromUid(String scheduledFromUid) {
        this.scheduledFromUid = scheduledFromUid;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    public boolean isOmitLogs() {
        return omitLogs;
    }

    public void setOmitLogs(boolean omitLogs) {
        this.omitLogs = omitLogs;
    }

    public String marshall() {
        Gson builder =  new GsonBuilder().setPrettyPrinting().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return builder.toJson(this);
    }

    public static TaskDescriptor unmarshall(String s) {
        Gson builder =  new GsonBuilder().setPrettyPrinting().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return builder.fromJson(s,TaskDescriptor.class);
    }

    @Override
    public String toString() {
        return "TaskDescriptor{" +
                "beanOrClass='" + beanOrClass + '\'' +
                ", buildVersion='" + buildVersion + '\'' +
                ", configClsName='" + configClsName + '\'' +
                ", requiredVersion='" + requiredVersion + '\'' +
                ", configuration='" + configuration + '\'' +
                ", runAfter=" + runAfter +
                ", startTime=" + startTime +
                ", completeTime=" + completeTime +
                ", status='" + status + '\'' +
                ", scheduledFromUid='" + scheduledFromUid + '\'' +
                ", tags=" + tags +
                ", omitLogs=" + omitLogs +
                ", context=" + context +
                '}';
    }
}
