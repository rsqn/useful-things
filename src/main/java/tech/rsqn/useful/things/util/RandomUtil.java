package tech.rsqn.useful.things.util;

import java.util.Random;

public class RandomUtil {
    private static Random g_rn = new Random(System.currentTimeMillis());

    public static int getRange(int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo > hi");
        }
        long range = (long) hi - (long) lo + 1;
        long frac = (long) (range * g_rn.nextDouble());
        return (int) (frac + lo);
    }

    public static long getRange(long lo, long hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo > hi");
        }
        long range = (long) hi - (long) lo + 1;
        long frac = (long) (range * g_rn.nextDouble());
        return (long) (frac + lo);
    }

}
