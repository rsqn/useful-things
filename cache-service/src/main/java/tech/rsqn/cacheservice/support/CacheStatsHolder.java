package tech.rsqn.cacheservice.support;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 16/03/12
 *
 * To change this template use File | Settings | File Templates.
 */
public class CacheStatsHolder {
    int reads;
    int writes;
    int invalidations;
    int hits;
    int misses;

    public void reset() {
        reads = 0;
        writes = 0;
        invalidations = 0;
        hits = 0;
        misses = 0;
    }

    @Override
    public String toString() {
        return "Cache Stats{" + "reads=" + reads + ", writes=" + writes +
        ", invalidations=" + invalidations + ", hits=" + hits + ", misses=" +
        misses + '}';
    }
}
