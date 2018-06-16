package tech.rsqn.cacheservice.support;

public interface ReadOperationUnlessKludge {
    boolean allowCaching(Object v);
}
