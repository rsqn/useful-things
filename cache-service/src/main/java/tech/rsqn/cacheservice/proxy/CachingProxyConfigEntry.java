/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.proxy;

import tech.rsqn.cacheservice.exceptions.CacheConfigurationRuntimeException;
import tech.rsqn.cacheservice.interceptors.InterceptorMetadata;

import java.text.MessageFormat;


/**
 * Author: mandrewes
 * Date: 12/08/11
 *
 *
 * @author mandrewes
 */
public class CachingProxyConfigEntry {
    private static final String delimiter = ",";
    private String methodName;
    private InterceptorMetadata.Operation operation;
    private String target;

    public void parseFromString(String s) {
        try {
            String[] toks = s.trim().split(delimiter);

            if (toks.length != 3) {
                throw new CacheConfigurationRuntimeException(MessageFormat.format(
                        "Invalid Configuration {0}, configuration must follow the format METHODNAME(n/a),OPERATION(read/write/invalidate),TARGET(n/a)  ie   myMethod,read,myCache",
                        s));
            }

            methodName = toks[0];
            operation = InterceptorMetadata.Operation.valueOf(toks[1]);
            target = toks[2];

            if (operation == null) {
                throw new CacheConfigurationRuntimeException(MessageFormat.format(
                        "Invalid Configuration {0}, configuration must follow the format METHODNAME(n/a),OPERATION(read/write/invalidate),TARGET(n/a)  ie   myMethod,read,myCache",
                        s));
            }
        } catch (Exception e) {
            throw new CacheConfigurationRuntimeException(MessageFormat.format(
                    "Invalid Configuration {0}, configuration must follow the format METHODNAME(n/a),OPERATION(read/write/invalidate),TARGET(n/a)  ie   myMethod,read,myCache",
                    s), e);
        }
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public InterceptorMetadata.Operation getOperation() {
        return operation;
    }

    public void setOperation(InterceptorMetadata.Operation operation) {
        this.operation = operation;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public String toString() {
        return "CachingProxyConfigEntry{" + "methodName='" + methodName + '\'' +
        ", operation=" + operation + ", target='" + target + '\'' + '}';
    }
}
