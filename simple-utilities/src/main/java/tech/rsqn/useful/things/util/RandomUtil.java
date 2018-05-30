package tech.rsqn.useful.things.util;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 4/18/13
 * Time: 7:03 PM
 * To change this template use File | Settings | File Templates.
 */
public class RandomUtil {
    private static SecureRandom g_rn = new SecureRandom(new Date().toString().getBytes());

    public static String getRandomString(int length) {
        int lo = 48;
        int hi = 122;
        String ret = "";
        for (int i = 0; i < length; i++) {
            int value = getRange(lo, hi);
            if (acceptableChar(value)) {
                ret += (new Character((char) value).toString());
            } else {
                i--;
            }
        }
        return ret;

    }

    public static boolean acceptableChar(int value) {
        if (value >= 48 && value <= 57)
            return true;

        if (value >= 65 && value <= 90)
            return true;

        if (value >= 97 && value <= 122)
            return true;

        return false;

    }

    public static byte[] getRandomBytes(int length) {
        int lo = 0;
        int hi = 255;

        byte[] ret = new byte[length];

        for (int i = 0; i < length; i++) {
            int value = getRange(lo, hi);
            ret[i] = (byte) value;
        }
        return ret;

    }

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
