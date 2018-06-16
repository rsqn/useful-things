/*
 *
 *
 * Author: mandrewes
 *
 */
package tech.rsqn.cacheservice.support;


/**
 * Author: mandrewes
 * Date: 17/06/11
 *
 * <p/>
 * Aids in building up keys from multiple values
 *
 * @author mandrewes
 */
public class DelimitedKey {
    public static final String delimiter = ".";
    private String s;

    protected DelimitedKey() {
    }

    public static DelimitedKey with(Object o) {
        DelimitedKey builder = new DelimitedKey();
        builder.s = o.toString();

        return builder;
    }

    public DelimitedKey and(Object o) {
        s += (delimiter + o.toString());

        return this;
    }

    @Override
    public String toString() {
        return s;
    }
}
