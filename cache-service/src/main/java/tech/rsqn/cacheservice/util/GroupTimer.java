package tech.rsqn.cacheservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;



public class GroupTimer {
    private Logger log = LoggerFactory.getLogger(getClass());
    Map<String, Timer> timers;

    public GroupTimer() {
        timers = new HashMap<String, Timer>();
    }

    private void log(String s) {
        log.info(s);
    }

    public void start(String name) {
        log("Start " + name);

        Timer timer = timers.get(name);

        if (timer == null) {
            timer = new Timer();
            timer.name = name;
            timers.put(name, timer);
        }

        timer.start();
    }

    public void stop(String name) {
        Timer timer = timers.get(name);

        if (timer == null) {
            return;
        }

        timer.stop();
    }

    public void stopAndReport(String name) {
        Timer timer = timers.get(name);

        if (timer == null) {
            return;
        }

        timer.stop();
        log(timer.report());
    }

    public void report(String name) {
        Timer timer = timers.get(name);

        if (timer == null) {
            return;
        }

        log(timer.report());
    }

    class Timer {
        String name;
        long startTime;
        long endTime;
        long totalTime = 0;
        long executions = 0;
        long min = -1;
        long max = -1;

        public void start() {
            startTime = System.currentTimeMillis();
        }

        public void stop() {
            endTime = System.currentTimeMillis();
            totalTime += (endTime - startTime);

            long diff = endTime - startTime;

            if ((diff < min) || (min == -1)) {
                min = diff;
            }

            if ((diff > max) || (max == -1)) {
                max = diff;
            }

            executions++;
        }

        private String fmt(long l) {
            if (l > 1000) {
                float v = l / 1000;

                return v + "s";
            }

            return l + "ms";
        }

        private String fmt(float l) {
            if (l > 1000) {
                float v = l / 1000;

                return v + "s";
            }

            return l + "ms";
        }

        public String report() {
            String ret = "";
            long taken = endTime - startTime;

            ret += (name + " " + fmt(taken));

            ret += (" total " + fmt(totalTime) + " for " + executions +
            " executions");

            if (executions > 0) {
                float avg = totalTime / executions;
                ret += (" avg " + fmt(avg));
            }

            if (executions > 0) {
                ret += (" min " + fmt(min));
                ret += (" max " + fmt(max));
            }

            return ret;
        }
    }
}
