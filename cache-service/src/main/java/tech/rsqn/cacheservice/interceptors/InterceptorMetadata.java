/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.interceptors;


/**
 * Author: mandrewes
 * Date: 12/08/11
 */
public class InterceptorMetadata {
    private String target;
    private Operation operation;

    public static InterceptorMetadata with(ReadInterceptor r, String target) {
        InterceptorMetadata ret = new InterceptorMetadata();
        ret.setOperation(Operation.Read);
        ret.setTarget(target);

        return ret;
    }

    public static InterceptorMetadata with(WriteInterceptor r, String target) {
        InterceptorMetadata ret = new InterceptorMetadata();
        ret.setOperation(Operation.Write);
        ret.setTarget(target);

        return ret;
    }

    public static InterceptorMetadata with(InvalidatingInterceptor r,
        String target) {
        InterceptorMetadata ret = new InterceptorMetadata();
        ret.setOperation(Operation.Invalidate);
        ret.setTarget(target);

        return ret;
    }

    public static InterceptorMetadata with(Operation operation, String target) {
        InterceptorMetadata ret = new InterceptorMetadata();
        ret.setOperation(operation);
        ret.setTarget(target);

        return ret;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }
    public enum Operation {Read,
        Write,
        Invalidate;
    }
}
