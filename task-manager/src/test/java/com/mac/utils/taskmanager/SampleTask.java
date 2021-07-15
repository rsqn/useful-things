package com.mac.utils.taskmanager;

import org.testng.annotations.Test;

/**
 * Created with IntelliJ IDEA.
 * Date: 11/04/13
 * Time: 10:33 AM
 * To change this template use File | Settings | File Templates.
 */
@Test(groups = {"unit-test"})
public class SampleTask extends Task {

    @Override
    public void run() {
        System.out.println("running");
    }
}
