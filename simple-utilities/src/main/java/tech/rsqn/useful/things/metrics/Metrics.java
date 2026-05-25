package tech.rsqn.useful.things.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * Modernized Metrics helper using Micrometer.
 * Maintains the preferred static, class-based interface from useful-things.
 * Java 11 compatible.
 */
public class Metrics {
    private static boolean startedReporting = false;

    public static Counter counter(Class<?> c, String name) {
        return counter(c.getName() + "." + name);
    }

    public static Counter counter(String name, String... tags) {
        return io.micrometer.core.instrument.Metrics.counter(name, tags);
    }

    public static void increment(String name, String... tags) {
        counter(name, tags).increment();
    }

    public static void increment(String name, double amount, String... tags) {
        counter(name, tags).increment(amount);
    }

    public static void increment(Class<?> c, String name) {
        counter(c, name).increment();
    }

    public static void increment(Class<?> c, String name, double amount) {
        counter(c, name).increment(amount);
    }

    public static Timer timer(Class<?> c, String name) {
        return timer(c.getName() + "." + name);
    }

    public static Timer timer(String name, String... tags) {
        return io.micrometer.core.instrument.Metrics.timer(name, tags);
    }

    /**
     * Compatibility method for MetricsCollector.record
     */
    public static void record(String name, long amount, TimeUnit unit, String... tags) {
        timer(name, tags).record(amount, unit);
    }

    public static void record(String name, long durationNanos) {
        record(name, durationNanos, TimeUnit.NANOSECONDS);
    }

    public static <T> T gauge(String name, T stateObject, ToDoubleFunction<T> valueFunction, String... tags) {
        return io.micrometer.core.instrument.Metrics.gauge(name, Tags.of(tags), stateObject, valueFunction);
    }

    public static void gauge(String name, double value) {
        io.micrometer.core.instrument.Metrics.gauge(name, value);
    }

    public static void summary(String name, double amount, String... tags) {
        io.micrometer.core.instrument.Metrics.summary(name, tags).record(amount);
    }

    public static MeterRegistry getRegistry() {
        return io.micrometer.core.instrument.Metrics.globalRegistry;
    }

    public static void clear() {
        getRegistry().clear();
    }

    public static String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== PERFORMANCE METRICS ===\n");
        sb.append(String.format("%-60s | %-10s | %-10s | %-10s | %-10s\n", "Metric", "Count", "Avg", "Min", "Max"));
        sb.append("----------------------------------------------------------------------------------------------------\n");

        for (Meter meter : getRegistry().getMeters()) {
            String name = meter.getId().getName();
            String tags = meter.getId().getTags().isEmpty() ? "" : " " + meter.getId().getTags().toString();
            String displayName = name + tags;

            if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                long count = timer.count();
                if (count > 0) {
                    double avg = timer.mean(TimeUnit.MICROSECONDS);
                    double max = timer.max(TimeUnit.MICROSECONDS);
                    sb.append(String.format("%-60s | %10d | %10.2f | %10s | %10.2f (us)\n",
                            displayName, count, avg, "-", max));
                }
            } else if (meter instanceof Counter) {
                Counter counter = (Counter) meter;
                sb.append(String.format("%-60s | %10.0f | %10s | %10s | %10s (count)\n",
                        displayName, counter.count(), "-", "-", "-"));
            } else if (meter instanceof DistributionSummary) {
                DistributionSummary summary = (DistributionSummary) meter;
                sb.append(String.format("%-60s | %10d | %10.2f | %10s | %10.2f (val)\n",
                        displayName, summary.count(), summary.mean(), "-", summary.max()));
            } else if (meter instanceof Gauge) {
                Gauge gauge = (Gauge) meter;
                sb.append(String.format("%-60s | %10s | %10.2f | %10s | %10s (gauge)\n",
                        displayName, "-", gauge.value(), "-", "-"));
            }
        }
        return sb.toString();
    }

    public static Map<String, MetricSnapshot> getSnapshot() {
        Map<String, MetricSnapshot> snapshot = new HashMap<>();
        for (Meter meter : getRegistry().getMeters()) {
            if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
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

    public static class MetricSnapshot {
        private final long count;
        private final double avgMicros;
        private final double maxMicros;

        public MetricSnapshot(long count, double avgMicros, double maxMicros) {
            this.count = count;
            this.avgMicros = avgMicros;
            this.maxMicros = maxMicros;
        }

        public long getCount() { return count; }
        public double getAvgMicros() { return avgMicros; }
        public double getMaxMicros() { return maxMicros; }
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
