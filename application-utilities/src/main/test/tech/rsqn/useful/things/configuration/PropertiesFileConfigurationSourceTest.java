package tech.rsqn.useful.things.configuration;

import tech.rsqn.useful.things.ConfigurationSourceTest;
import tech.rsqn.useful.things.configuration.ConfigurationSource;
import tech.rsqn.useful.things.configuration.PropertiesFileConfigurationSource;

import java.util.ArrayList;

public class PropertiesFileConfigurationSourceTest extends ConfigurationSourceTest {

    @Override
    protected ConfigurationSource getConfigurationSource() {
        PropertiesFileConfigurationSource propertiesFileConfigurationSource = new PropertiesFileConfigurationSource(
            "configSourceTest.properties");

        propertiesFileConfigurationSource.init();
        return propertiesFileConfigurationSource;
    }
}
