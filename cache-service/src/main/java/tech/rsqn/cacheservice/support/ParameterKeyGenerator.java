/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;


/**
 * Author: mandrewes
 * Date: 15/08/11
 *
 *
 * used to generate keys for parameters to methods that have complex types as parameters, ie web services.
 * @author mandrewes
 */
public interface ParameterKeyGenerator<T> {
    public abstract boolean supportsClass(Class c);

    public abstract String generateParamKey(T param);
}
