package tech.rsqn.useful.things.metrics;

import com.codahale.metrics.*;
import tech.rsqn.useful.things.concurrency.ThreadUtil;
import tech.rsqn.useful.things.util.SysInfo;

import java.util.concurrent.TimeUnit;

public class Metrics {
    final static MetricRegistry registry = new MetricRegistry();
    private static boolean startedReporting = false;

    public static Counter counter(Class c, String name) {
        return registry.counter(MetricRegistry.name(c, name));
    }

    public static Timer timer(Class c, String name) {
        return registry.timer(MetricRegistry.name(c, name));
    }

    public static Meter meter(Class c, String name) {
        return registry.meter(MetricRegistry.name(c, name));
    }


    public static synchronized void startReporting() {
        if (!startedReporting) {
            startedReporting = true;
            ConsoleReporter reporter = ConsoleReporter.forRegistry(registry).build();
            reporter.start(1, TimeUnit.MINUTES);
        }
        Thread t = new Thread() {
            @Override
            public void run() {
                SysInfo info = new SysInfo();

                while(true) {
                    System.out.println(info.MemInfo());
                    ThreadUtil.doSleep(60L*1000L*5L);
                }
            }
        };

        t.setDaemon(true);
        t.start();
    }



}
