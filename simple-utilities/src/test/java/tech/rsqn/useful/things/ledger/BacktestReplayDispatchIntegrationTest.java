package tech.rsqn.useful.things.ledger;

import com.google.gson.JsonParser;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import tech.rsqn.useful.things.metrics.Metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emulates the pysol backtest hot path more closely:
 * <ul>
 *   <li>replay read → {@code advanceTo} task-list scan with Micrometer counters (like {@code PysolScheduler})</li>
 *   <li>per-event timers (scheduler + dispatch) like {@code BacktestService}</li>
 *   <li>write to 3 {@link WriteBehindDiskLedger}s with sync notifications</li>
 *   <li>subscriber mix: analyzer (striped tick submit) + order-book cache + volume window + dashboard</li>
 *   <li>striped tick lanes with CallerRuns backpressure (like {@code UnifiedTickExecutor})</li>
 * </ul>
 * Run: {@code mvn -pl simple-utilities test -Pintegration}
 */
public class BacktestReplayDispatchIntegrationTest {

    private static final RecordType TYPE_DATA = RecordType.of("MARKET_DATA");
    private static final RecordType TYPE_TRADES = RecordType.of("MARKET_TRADES");
    private static final RecordType TYPE_BOOKS = RecordType.of("MARKET_ORDER_BOOKS");

    private static final int MIX_BOOKS = 82;
    private static final int MIX_DATA = 13;

    private static final int EVENT_COUNT = 100_000;
    private static final int PAYLOAD_BYTES = 1024;
    /** Typical backtest has many scheduled tasks; each advanceTo scans all of them. */
    private static final int SCHEDULER_TASK_COUNT = 64;
    private static final int SYMBOL_CARDINALITY = 50;
    private static final int TICK_LANES = 20;
    private static final int TICK_QUEUE_PER_LANE = 64;
    /**
     * When true, TradingPair.processEvent proxy runs on the replay thread inside the
     * sync subscriber (dominates wall clock toward ~33k). When false, only striped submit
     * is used (harness stays too fast — tick lanes stay idle).
     */
    private static final boolean INLINE_PROCESS_EVENT = true;
    /** Extra Gson parse stand-in for per-tick indicator/CDSL work on the sync path. */
    private static final int INLINE_PARSE_REPS = 22;
    private static final int WRITE_QUEUE_CAPACITY = 2_000;
    private static final double MIN_EVENTS_PER_SEC = 5_000.0;
    /** Accept band around pysol full backtest (~33k); fail if still trivially fast. */
    private static final double TARGET_MAX_EVENTS_PER_SEC = 45_000.0;

    private static final String SYNC_PROP = "pysol.ledger.sync-notifications";

    private Path tempDir;
    private LedgerRegistry registry;
    private String previousSyncProp;
    private StripedTickExecutor tickExecutor;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws IOException {
        previousSyncProp = System.getProperty(SYNC_PROP);
        System.setProperty(SYNC_PROP, "true");
        Metrics.clear();

        tempDir = Files.createTempDirectory("backtest-replay-dispatch");
        registry = new LedgerRegistry();
        registry.registerRecordType(TYPE_DATA, StreamRecord.class);
        registry.registerRecordType(TYPE_TRADES, StreamRecord.class);
        registry.registerRecordType(TYPE_BOOKS, StreamRecord.class);
        tickExecutor = new StripedTickExecutor(TICK_LANES, TICK_QUEUE_PER_LANE);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws IOException {
        if (tickExecutor != null) {
            tickExecutor.shutdown();
            tickExecutor = null;
        }
        if (previousSyncProp == null) {
            System.clearProperty(SYNC_PROP);
        } else {
            System.setProperty(SYNC_PROP, previousSyncProp);
        }
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // best-effort
                        }
                    });
        }
    }

    @Test(groups = "integration")
    public void integration_backtestReplayDispatch_writesThreeLedgersAndNotifies() throws Exception {
        Path replayFile = tempDir.resolve("backtest_replay.jsonl");
        Path dataFile = tempDir.resolve("market_data.jsonl");
        Path tradesFile = tempDir.resolve("market_trades.jsonl");
        Path booksFile = tempDir.resolve("market_order_books.jsonl");

        writeSyntheticReplay(replayFile, EVENT_COUNT);

        WriteBehindDiskLedger<StreamRecord> dataLedger = openWriteBehind(TYPE_DATA, dataFile);
        WriteBehindDiskLedger<StreamRecord> tradesLedger = openWriteBehind(TYPE_TRADES, tradesFile);
        WriteBehindDiskLedger<StreamRecord> booksLedger = openWriteBehind(TYPE_BOOKS, booksFile);

        ConcurrentHashMap<String, AtomicLong> seriesBySymbol = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, StreamRecord> latestBooks = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Deque<long[]>> volumeSamples = new ConcurrentHashMap<>();
        AtomicInteger analyzerNotifies = new AtomicInteger();
        AtomicInteger bookCacheNotifies = new AtomicInteger();
        AtomicInteger volumeNotifies = new AtomicInteger();
        AtomicInteger dashboardNotifies = new AtomicInteger();
        AtomicLong processEventRuns = new AtomicLong();
        AtomicLong callerRuns = new AtomicLong();

        // MARKET_DATA: analyzer + dashboard (ConsoleDashboardService)
        dataLedger.subscribe(null, r -> analyzerSubscribe(r, seriesBySymbol, analyzerNotifies, processEventRuns, callerRuns));
        dataLedger.subscribe(null, r -> {
            dashboardNotifies.incrementAndGet();
            Metrics.increment("harness.dashboard.count.tick");
        });

        // MARKET_TRADES: analyzer + VolumeAccumulator-style window
        tradesLedger.subscribe(null, r -> analyzerSubscribe(r, seriesBySymbol, analyzerNotifies, processEventRuns, callerRuns));
        tradesLedger.subscribe(null, r -> {
            volumeNotifies.incrementAndGet();
            onTradeVolume(r, volumeSamples);
        });

        // MARKET_ORDER_BOOKS: analyzer + BacktestOrderBookService cache
        booksLedger.subscribe(null, r -> analyzerSubscribe(r, seriesBySymbol, analyzerNotifies, processEventRuns, callerRuns));
        booksLedger.subscribe(null, r -> {
            bookCacheNotifies.incrementAndGet();
            String symbol = symbolOf(r);
            latestBooks.put(symbol, r);
        });

        Map<RecordType, WriteBehindDiskLedger<StreamRecord>> destinations = new HashMap<>();
        destinations.put(TYPE_DATA, dataLedger);
        destinations.put(TYPE_TRADES, tradesLedger);
        destinations.put(TYPE_BOOKS, booksLedger);

        StubScheduler scheduler = StubScheduler.withPeriodicTasks(SCHEDULER_TASK_COUNT, 60_000L);
        AtomicLong dispatched = new AtomicLong(0);
        AtomicLong lagGateChecks = new AtomicLong(0);

        DiskPersistenceDriver<StreamRecord> reader = new DiskPersistenceDriver<>(replayFile, registry);
        reader.init();
        reader.start();

        long startMs = System.currentTimeMillis();
        try {
            reader.read(-1, record -> {
                long tLoop = System.nanoTime();
                long ts = record.getTimestamp() != null ? record.getTimestamp().toEpochMilli() : dispatched.get();

                long t0 = System.nanoTime();
                scheduler.advanceTo(ts);
                Metrics.record("harness.backtest.event.scheduler", System.nanoTime() - t0, TimeUnit.NANOSECONDS);

                // lag gate stub (enabled path touches atomics + metric)
                long tLag = System.nanoTime();
                lagGateChecks.incrementAndGet();
                Metrics.increment("harness.lag_gate.count.check");
                Metrics.record("harness.backtest.event.lag_gate", System.nanoTime() - tLag, TimeUnit.NANOSECONDS);

                long tWrite = System.nanoTime();
                WriteBehindDiskLedger<StreamRecord> dest = destinations.get(record.getType());
                Assert.assertNotNull(dest, "unknown record type: " + record.getType());
                dest.write(record);
                Metrics.record("harness.backtest.event.ledger_write", System.nanoTime() - tWrite, TimeUnit.NANOSECONDS);

                dispatched.incrementAndGet();
                Metrics.record("harness.backtest.event.loop", System.nanoTime() - tLoop, TimeUnit.NANOSECONDS);
                return true;
            });
        } finally {
            reader.close();
        }

        dataLedger.flush();
        tradesLedger.flush();
        booksLedger.flush();
        tickExecutor.shutdown();
        Assert.assertTrue(tickExecutor.awaitTermination(30, TimeUnit.SECONDS), "tick executor did not drain");
        tickExecutor = null;

        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
        double eventsPerSec = dispatched.get() * 1000.0 / elapsedMs;

        System.out.printf(
                "BacktestReplayDispatch: events=%d elapsedMs=%d events/s=%.0f "
                        + "(pysol full backtest ~33k) advanceTo=%d taskChecks=%d "
                        + "analyzer=%d booksCache=%d volume=%d dashboard=%d "
                        + "processEvent=%d callerRuns=%d lagChecks=%d symbols=%d%n",
                dispatched.get(),
                elapsedMs,
                eventsPerSec,
                scheduler.advanceToCalls.get(),
                scheduler.taskDueChecks.get(),
                analyzerNotifies.get(),
                bookCacheNotifies.get(),
                volumeNotifies.get(),
                dashboardNotifies.get(),
                processEventRuns.get(),
                callerRuns.get(),
                lagGateChecks.get(),
                seriesBySymbol.size());

        Assert.assertEquals(dispatched.get(), EVENT_COUNT);
        Assert.assertEquals(scheduler.advanceToCalls.get(), EVENT_COUNT);
        Assert.assertEquals(scheduler.taskDueChecks.get(), (long) EVENT_COUNT * SCHEDULER_TASK_COUNT);
        Assert.assertEquals(analyzerNotifies.get(), EVENT_COUNT);
        Assert.assertTrue(bookCacheNotifies.get() > 0);
        Assert.assertTrue(volumeNotifies.get() > 0);
        Assert.assertTrue(dashboardNotifies.get() > 0);
        Assert.assertEquals(processEventRuns.get(), EVENT_COUNT);

        Assert.assertTrue(Files.size(dataFile) > 0);
        Assert.assertTrue(Files.size(tradesFile) > 0);
        Assert.assertTrue(Files.size(booksFile) > 0);

        Assert.assertTrue(
                eventsPerSec >= MIN_EVENTS_PER_SEC,
                String.format("throughput too low: %.0f events/s (min %.0f)", eventsPerSec, MIN_EVENTS_PER_SEC));
        Assert.assertTrue(
                eventsPerSec <= TARGET_MAX_EVENTS_PER_SEC,
                String.format(
                        "harness still too fast vs ~33k backtest: %.0f events/s (max %.0f) — add sync-path cost",
                        eventsPerSec,
                        TARGET_MAX_EVENTS_PER_SEC));

        dataLedger.close();
        tradesLedger.close();
        booksLedger.close();
    }

    private void analyzerSubscribe(
            StreamRecord record,
            ConcurrentHashMap<String, AtomicLong> seriesBySymbol,
            AtomicInteger analyzerNotifies,
            AtomicLong processEventRuns,
            AtomicLong callerRuns) {
        analyzerNotifies.incrementAndGet();
        Metrics.increment("harness.stage03.count.received");
        String symbol = symbolOf(record);
        seriesBySymbol.computeIfAbsent(symbol, s -> new AtomicLong());

        Runnable pairWork = () -> {
            processEventRuns.incrementAndGet();
            processEventProxy(record);
        };

        if (INLINE_PROCESS_EVENT) {
            // Sync path pays pair work here — matches observed backtest wall clock better
            // than async lanes (which stay idle while the feeder is the limiter).
            pairWork.run();
            tickExecutor.submit(symbol, () -> {
                // still exercise striped handoff cost
            }, callerRuns);
        } else {
            tickExecutor.submit(symbol, pairWork, callerRuns);
        }
    }

    /** Stand-in for TradingPair.processEvent + light CDSL/indicator work. */
    private static void processEventProxy(StreamRecord record) {
        String data = record.getData();
        String snippet = data.length() > 128 ? data.substring(0, 128) : data;
        String json = "{\"symbol\":\"" + symbolOf(record) + "\",\"v\":" + record.getValue()
                + ",\"d\":\"" + snippet + "\"}";
        for (int i = 0; i < INLINE_PARSE_REPS; i++) {
            JsonParser.parseString(json);
        }
        Metrics.increment("harness.processEvent.count.ran");
    }

    private static void onTradeVolume(StreamRecord record, ConcurrentHashMap<String, Deque<long[]>> samples) {
        Metrics.increment("harness.volume.count.on_trade");
        String symbol = symbolOf(record);
        long ts = record.getTimestamp() != null ? record.getTimestamp().toEpochMilli() : 0L;
        long quote = Math.abs(record.getValue()) + 1L;
        Deque<long[]> deque = samples.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        synchronized (deque) {
            long cutoff = ts - 3_600_000L;
            while (!deque.isEmpty() && deque.peekFirst()[0] < cutoff) {
                deque.removeFirst();
            }
            deque.addLast(new long[]{ts, quote});
        }
    }

    private static String symbolOf(StreamRecord record) {
        return "SYM" + Math.floorMod(record.getValue(), SYMBOL_CARDINALITY);
    }

    private void writeSyntheticReplay(Path replayFile, int count) throws Exception {
        DiskPersistenceDriver<StreamRecord> writer = new DiskPersistenceDriver<>(replayFile, registry);
        writer.setAutoFlush(false);
        writer.setFlushIntervalWrites(20_000);
        writer.init();
        writer.start();
        try {
            String bookPayload = "x".repeat(PAYLOAD_BYTES);
            String smallPayload = "x".repeat(256);
            Instant base = Instant.parse("2026-05-21T00:00:00Z");
            for (int i = 0; i < count; i++) {
                int bucket = i % 100;
                RecordType type;
                String payload;
                if (bucket < MIX_BOOKS) {
                    type = TYPE_BOOKS;
                    payload = bookPayload;
                } else if (bucket < MIX_BOOKS + MIX_DATA) {
                    type = TYPE_DATA;
                    payload = smallPayload;
                } else {
                    type = TYPE_TRADES;
                    payload = smallPayload;
                }
                writer.write(new StreamRecord(type, base.plusMillis(i), payload, i));
            }
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private WriteBehindDiskLedger<StreamRecord> openWriteBehind(RecordType type, Path file) throws IOException {
        DiskPersistenceDriver<StreamRecord> driver = new DiskPersistenceDriver<>(file, registry);
        driver.setAutoFlush(false);
        driver.setFlushIntervalWrites(20_000);
        driver.setFlushIntervalSeconds(5.0);
        driver.init();
        driver.start();
        WriteBehindDiskLedger<StreamRecord> ledger = new WriteBehindDiskLedger<>(type, driver);
        ledger.setWriteQueueCapacity(WRITE_QUEUE_CAPACITY);
        ledger.init();
        return ledger;
    }

    /**
     * Mirrors {@code PysolScheduler.advanceTo}, including Micrometer increments on the hot path.
     */
    static final class StubScheduler {
        private final List<StubTask> tasks;
        final AtomicLong advanceToCalls = new AtomicLong();
        final AtomicLong taskDueChecks = new AtomicLong();
        final AtomicLong taskFires = new AtomicLong();

        private StubScheduler(List<StubTask> tasks) {
            this.tasks = tasks;
        }

        static StubScheduler withPeriodicTasks(int taskCount, long intervalMs) {
            List<StubTask> tasks = new ArrayList<>(taskCount);
            long base = Instant.parse("2026-05-21T00:00:00Z").toEpochMilli();
            for (int i = 0; i < taskCount; i++) {
                tasks.add(new StubTask("task-" + i, intervalMs, base));
            }
            return new StubScheduler(tasks);
        }

        void advanceTo(long epochMillis) {
            Metrics.increment("harness.stage08.scheduler.count.advanceTo_called");
            advanceToCalls.incrementAndGet();
            for (StubTask task : tasks) {
                Metrics.increment("harness.stage08.scheduler.count.task_due_check");
                taskDueChecks.incrementAndGet();
                long elapsed = epochMillis - task.lastFiredMs;
                if (elapsed < task.intervalMs) {
                    if (elapsed < 0) {
                        task.lastFiredMs = epochMillis;
                    }
                    continue;
                }
                int fireCount = (int) (elapsed / task.intervalMs);
                taskFires.addAndGet(fireCount);
                task.lastFiredMs += (long) fireCount * task.intervalMs;
            }
        }
    }

    private static final class StubTask {
        final String name;
        final long intervalMs;
        long lastFiredMs;

        StubTask(String name, long intervalMs, long lastFiredMs) {
            this.name = name;
            this.intervalMs = intervalMs;
            this.lastFiredMs = lastFiredMs;
        }
    }

    /** Striped lanes + CallerRunsPolicy, like {@code UnifiedTickExecutor}. */
    static final class StripedTickExecutor {
        private final List<ThreadPoolExecutor> lanes;
        private final int laneCount;

        StripedTickExecutor(int laneCount, int perLaneCap) {
            this.laneCount = laneCount;
            this.lanes = new ArrayList<>(laneCount);
            for (int i = 0; i < laneCount; i++) {
                final int idx = i;
                lanes.add(new ThreadPoolExecutor(
                        1, 1,
                        60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(perLaneCap),
                        r -> {
                            Thread t = new Thread(r, "harness-tick-" + idx);
                            t.setDaemon(true);
                            return t;
                        },
                        new ThreadPoolExecutor.CallerRunsPolicy()));
            }
        }

        void submit(String symbol, Runnable task, AtomicLong callerRuns) {
            Metrics.increment("harness.stage04.tick_executor.count.task_submitted");
            int lane = Math.floorMod(symbol.hashCode(), laneCount);
            ThreadPoolExecutor exec = lanes.get(lane);
            long submitTime = System.nanoTime();
            exec.execute(() -> {
                if (Thread.currentThread().getName().startsWith("harness-tick-")) {
                    // normal lane
                } else {
                    callerRuns.incrementAndGet();
                    Metrics.increment("harness.stage04.tick_executor.count.caller_runs");
                }
                long start = System.nanoTime();
                Metrics.record("harness.stage04.tick_executor.duration.queue_wait", start - submitTime, TimeUnit.NANOSECONDS);
                try {
                    task.run();
                } finally {
                    Metrics.increment("harness.stage04.tick_executor.count.task_executed");
                    Metrics.record("harness.stage04.tick_executor.duration.task_run", System.nanoTime() - start, TimeUnit.NANOSECONDS);
                }
            });
        }

        void shutdown() {
            for (ThreadPoolExecutor lane : lanes) {
                lane.shutdown();
            }
        }

        boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            for (ThreadPoolExecutor lane : lanes) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !lane.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class StreamRecord extends Record {
        private String data;
        private int value;

        public StreamRecord() {
        }

        public StreamRecord(RecordType type, Instant timestamp, String data, int value) {
            setType(type);
            setTimestamp(timestamp);
            this.data = data;
            this.value = value;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }
}
