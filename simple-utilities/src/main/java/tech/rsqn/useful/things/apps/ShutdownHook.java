package tech.rsqn.useful.things.apps;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class ShutdownHook {

    private final Logger logger_ = LoggerFactory.getLogger(getClass());

    private String name_;
    private Callable callable_;
    private Integer priority_;

    protected ShutdownHook(String name, Callable callable, Integer priority) {
        name_ = name;
        callable_ = callable;
        setPriority(priority);
    }

    public String getName() {
        return name_;
    }

    public Callable getCallable() {
        return callable_;
    }

    public void call() {
        try {
            logger_.info("Calling shutdown hook " + getName());
            getCallable().call();
        } catch (Exception ignore) {
            logger_.warn("Exception calling hook for " + getName() + " - " + ignore.getMessage(), ignore);
        }
    }

    public Integer getPriority() {
        return priority_;
    }

    private void setPriority(Integer priority) {
        if (priority == null)
            priority_ = 40;
        else if (priority <= 0)
            priority_ = 1;
        else if (priority > 100)
            priority_ = 100;
        else
            priority_ = priority;
    }
}
