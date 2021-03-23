package tech.rsqn.useful.things.configuration;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class LayeredConfigurationSource extends AbstractConfigurationSource {
    private List<ConfigurationSource> layers;

    public LayeredConfigurationSource() {
        this.layers = new CopyOnWriteArrayList<>();
    }

    public void append(ConfigurationSource src) {
        layers.add(src);
    }

    @Override
    public String getEnvironment() {
        return System.getProperty("env");
    }


    @Override
    public String getStringValue(String name) {
        // layers only grow so we don't need to lock here in the unlikely case of a startup race condition
        for (int i = layers.size()-1; i >=0 ; i--) {
            ConfigurationSource layer = layers.get(i);
            if (layer.hasValue(name)) {
                return layer.getStringValue(name);
            }
        }
        return null;
    }

    @Override
    public String getStringValue(String name, String dfl) {
        String ret = getStringValue(name);
        if (StringUtils.isEmpty(ret)) {
            ret = dfl;
        }
        return ret;
    }


    public Map<String, String> asMap() {
        Map<String,String> ret = new HashMap<>();
        for (ConfigurationSource layer : layers) {
            ret.putAll(layer.asMap());
        }
        return ret;
    }

    @Override
    public List<String> getNames() {
        List<String> ret = new ArrayList();
        for (ConfigurationSource layer : layers) {
            for (String key : layer.asMap().keySet() ) {
                if ( ! ret.contains(key)) {
                    ret.add(key);
                }
            }
        }
        return ret;
    }

    @Override
    public void setValue(String name, String value) {
        // Does not make sense for this implementation
        throw new NotImplementedException("setValue not implemented - " + name);
    }

}

