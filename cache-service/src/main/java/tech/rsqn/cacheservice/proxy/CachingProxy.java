/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.proxy;

import org.springframework.aop.framework.ProxyFactoryBean;


/**
 * Author: mandrewes
 * Date: 12/08/11
 *
 *
 * In some cases where you cannot add annotations to the class, for example in the case of a generated client. You can
 * still cache perform caching by wrapping it in a proxy in spring configuration.
 *
 * This is cleaner than pointcuts due to the level of granularity and configuration needed.
 *
 * @author mandrewes
 */
public class CachingProxy extends ProxyFactoryBean {
}
