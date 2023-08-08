package tech.rsqn.useful.things.lambda;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


/**
 * Utility to wire in spring when using lambda functions
 */
public class LambdaSpringUtil {
    private static final Logger LOG = LoggerFactory.getLogger(LambdaSpringUtil.class);
    private static Object lck = new Object();

    private static String globalRootContextPath = "/spring/app-ctx.xml";
    private static ApplicationContext ctx = null;

    /**
     * Sets application context
     * @param _ctx
     */
    @Autowired
    public void setCtx(ApplicationContext _ctx) {
        // in lambda we need to start up the CTX ourselves
        // for unit tests and REST, all the autowiring should happen automatically
        if (LambdaSpringUtil.ctx == null) {
            LambdaSpringUtil.ctx = _ctx;
        }
    }


    /**
     * overides loading of xml applciation context
     * @param s
     */
    public static void setGlobalRootContextPath(String s) {
        globalRootContextPath = s;
    }

    /**
     *
     * @return
     */
    public static ApplicationContext getCtx() {
        return ctx;
    }

    /**
     * Wires spring into the bean passed in
     * @param o
     */
    public static void wireInSpring(Object o) {
        wireInSpring(o, o.getClass().getSimpleName());
    }

    /**
     * wires spring into the passed in bean
     * @param o
     * @param myBeanName
     */
    public static void wireInSpring(Object o, String myBeanName) {
        // Lambda does not do this for you - though serverless does have a library to do it
        if (ctx == null) {
            synchronized (lck) {
                if (ctx == null) {
                    LOG.info("LamdaSpringUtil CTX is null -  initialising spring");
                    ctx = new ClassPathXmlApplicationContext(globalRootContextPath);
                }
            }
        } else {
            LOG.debug("LamdaSpringUtil CTX is not null - not initialising spring");
        }
        AutowireCapableBeanFactory factory = ctx.getAutowireCapableBeanFactory();
        factory.autowireBean(o);
        factory.initializeBean(0, myBeanName);
    }
}
