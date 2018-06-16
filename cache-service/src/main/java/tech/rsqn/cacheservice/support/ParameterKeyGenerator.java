package tech.rsqn.cacheservice.support;


public interface ParameterKeyGenerator<T> {
    public abstract boolean supportsClass(Class c);

    public abstract String generateParamKey(T param);
}
