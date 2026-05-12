package tech.rsqn.useful.things.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Modernized Metrics helper using Micrometer.
 * Maintains the preferred static, class-based interface from useful-things.
 */
public class Metrics {
    private static boolean startedReporting = false;

    public static Counter counter(Class<?> c, String name) {
        return counter(c.getName() + "." + name);
    }

    public static Counter counter(String name) {
        return io.micrometer.core.instrument.Metrics.counter(name);
    }

    public static void increment(String name) {
        counter(name).increment();
    }

    public static void increment(Class<?> c, String name) {
        counter(c, name).increment();
    }

    public static Timer timer(Class<?> c, String name) {
        return timer(c.getName() + "." + name);
    }

    public static Timer timer(String name) {
        return io.micrometer.core.instrument.Metrics.timer(name);
    }

    /**
     * Compatibility method for MetricsCollector.record
     */
    public static void record(String name, long durationNanos) {
        timer(name).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public static MeterRegistry getRegistry() {
        return io.micrometer.core.instrument.Metrics.globalRegistry;
    }

    public static void clear() {
        getRegistry().clear();
    }

    public static String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== PERFORMANCE METRICS (Micros) ===\n");
        sb.append(String.format("%-40s | %-10s | %-10s | %-10s | %-10s\n", "Metric", "Count", "Avg", "Min", "Max"));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        for (Meter meter : getRegistry().getMeters()) {
            if (meter instanceof Timer timer) {
                long count = timer.count();
                if (count > 0) {
                    double avg = timer.mean(TimeUnit.MICROSECONDS);
                    double max = timer.max(TimeUnit.MICROSECONDS);
                    double min = 0.0; // Micrometer doesn't track min by default

                    sb.append(String.format("%-40s | %10d | %10.2f | %10.2f | %10.2f\n",
                            timer.getId().getName(),
                            count,
                            avg,
                            min,
                            max));
                }
            }
        }
        return sb.toString();
    }

    public static Map<String, MetricSnapshot> getSnapshot() {
        Map<String, MetricSnapshot> snapshot = new HashMap<>();
        for (Meter meter : getRegistry().getMeters()) {
            if (meter instanceof Timer timer) {
                long count = timer.count();
                if (count > 0) {
                    double avg = timer.mean(TimeUnit.MICROSECONDS);
                    double max = timer.max(TimeUnit.MICROSECONDS);
                    snapshot.put(timer.getId().getName(), new MetricSnapshot(count, avg, max));
                }
            }
        }
        return snapshot;
    }

    public record MetricSnapshot(long count, double avgMicros, double maxMicros) {
    }

    public static synchronized void startReporting() {
        if (!startedReporting) {
            startedReporting = true;
            
            Thread t = new Thread() {
                @Override
                public void run() {
                    tech.rsqn.useful.things.util.SysInfo info = new tech.rsqn.useful.things.util.SysInfo();
                    while(true) {
                        System.out.println(info.MemInfo());
                        tech.rsqn.useful.things.concurrency.ThreadUtil.doSleep(60L*1000L*5L);
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }
}
