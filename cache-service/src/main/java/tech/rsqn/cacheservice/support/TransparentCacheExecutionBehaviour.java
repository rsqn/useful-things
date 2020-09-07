package tech.rsqn.cacheservice.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransparentCacheExecutionBehaviour {
    //private static Logger log = LoggerFactory.getLogger(TransparentCacheExecutionBehaviour.class);
    public static final ThreadLocal<CacheBehaviour> threadLocal = new ThreadLocal();

    public static CacheBehaviour getBehaviour() {
        CacheBehaviour cb;
        cb = threadLocal.get();

        if (cb == null) {
            //            log.debug("no existing cache behaviours in ThreadLocal");
            cb = new CacheBehaviour();
            threadLocal.set(cb);
        }

        //        } else {
        //            log.debug("ThreadLocal cacheBehaviour is " + cb);
        //        }
        return cb;
    }

    public static void clearCacheAfterReads() {
        //log.info("Changing cache behaviour to clearCacheAfterReads");
        getBehaviour().clearCacheAfterRead();
    }

    public static void returnIfItemIsNotCached() {
        //log.info("Changing cache behaviour to returnIfItemIsNotCached");
        getBehaviour().returnIfItemIsNotCached();
    }

    public static void enableDefaultBehaviour() {
        //log.info("Changing cache behaviour to enableDefaultBehaviour");
        getBehaviour().enableDefaultBehaviour();
    }
}
