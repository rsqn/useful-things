package tech.rsqn.useful.things.configuration;

import com.google.common.base.Strings;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ConfigurationSource adapter for Spring's Environment.
 * Reads properties from Spring's property sources (application.properties, environment variables, etc.).
 */
public class SpringEnvironmentConfigurationSource extends AbstractConfigurationSource {

    private final Environment env;

    public SpringEnvironmentConfigurationSource(Environment env) {
        this.env = env;
    }

    @Override
    public String getStringValue(String name) {
        return env.getProperty(name);
    }

    @Override
    public String getStringValue(String name, String dfl) {
        String value = env.getProperty(name);
        return Strings.isNullOrEmpty(value) ? dfl : value;
    }

    @Override
    public String getEnvironment() {
        if (env.getActiveProfiles().length > 0) {
            return String.join(",", env.getActiveProfiles());
        }
        return env.getProperty("spring.profiles.active", "default");
    }

    @Override
    public List<String> getNames() {
        if (!(env instanceof ConfigurableEnvironment)) {
            return Collections.emptyList();
        }
        ConfigurableEnvironment configurable = (ConfigurableEnvironment) env;
        List<String> names = new ArrayList<>();
        for (PropertySource<?> source : configurable.getPropertySources()) {
            if (source instanceof EnumerablePropertySource) {
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }

    @Override
    public Map<String, String> asMap() {
        if (!(env instanceof ConfigurableEnvironment)) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : getNames()) {
            String value = env.getProperty(name);
            if (value != null) {
                map.put(name, value);
            }
        }
        return map;
    }

    @Override
    public void setValue(String name, String value) {
        throw new UnsupportedOperationException("Spring Environment is read-only; setValue not supported");
    }
}
