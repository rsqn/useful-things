package tech.rsqn.cacheservice.support;


/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: 6/01/12
 *
 *
 * this is a hack to get around an issue with SOS, where webservices fail but still return non-null values (but empty tree)
 *
 */
public interface ReadOperationUnlessKludge {
    boolean allowCaching(Object v);
}
