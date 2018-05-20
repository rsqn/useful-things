package tech.rsqn.useful.things.configuration;


import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: mandrewes
 * Date: Sep 28, 2012
 * To change this template use File | Settings | File Templates.
 */
public interface ConfigurationSource {

    boolean hasValue(String name);

    String getEnvironment();

    String getStringValue(String name);

    String getStringValue(String name, String dfl);

    int getIntValue(String name);

    int getIntValue(String name, int dfl);

    boolean getBoolValue(String name);

    boolean getBoolValue(String name, boolean dfl);

    Map<String, String> asMap();

    List<Integer> getIntArray(String name, String delimiter);

    List<String> getStringArray(String name, String delimiter);

    void setValue(String name, String value);
}

