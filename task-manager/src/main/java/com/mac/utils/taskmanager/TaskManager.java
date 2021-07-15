package com.mac.utils.taskmanager;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:08 AM
 * To change this template use File | Settings | File Templates.
 */
public interface TaskManager {

    public void submit(Task t);

    public void submit(Task t, Date d);
}
