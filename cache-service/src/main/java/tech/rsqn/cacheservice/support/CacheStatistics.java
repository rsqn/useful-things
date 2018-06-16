package tech.rsqn.cacheservice.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 15/03/12
 */
public class CacheStatistics {
    private static Logger log = LoggerFactory.getLogger(CacheStatistics.class);
    public static final ThreadLocal<CacheStatsHolder> threadLocal = new ThreadLocal();

    public static CacheStatsHolder getStats() {
        CacheStatsHolder cb;
        cb = threadLocal.get();

        if (cb == null) {
            cb = new CacheStatsHolder();
            cb.reset();
            threadLocal.set(cb);
        }

        return cb;
    }

    public static String getReport() {
        return getStats().toString();
    }

    public static void reset() {
        getStats().reset();
    }

    public static void incrementReads() {
        getStats().reads++;
    }

    public static void incrementWrites() {
        getStats().writes++;
    }

    public static void incrementHits() {
        getStats().hits++;
    }

    public static void incrementMisses() {
        getStats().misses++;
    }

    public static void incrementInvalidations() {
        getStats().invalidations++;
    }
}
