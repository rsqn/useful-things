package tech.rsqn.useful.things.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tech.rsqn.useful.things.util.EnvVarHelper;

import java.util.Arrays;

@Configuration
public class ConfigSourceConfig {

    private final Environment environment;

    public ConfigSourceConfig(final Environment environment) {
        this.environment = environment;
    }

    @Bean(name = "envVarHelper")
    public EnvVarHelper envVarHelper() {
        return new EnvVarHelper();
    }

    @Bean(name = "configSource")
    public LayeredConfigurationSource configSource() {
        final LayeredConfigurationSource configSource = new LayeredConfigurationSource();
        configSource.append(propertiesFileConfigurationSource());
        return configSource;
    }

    @Bean(name = "propertiesConfigSrc", initMethod = "init")
    public PropertiesFileConfigurationSource propertiesFileConfigurationSource() {
        final PropertiesFileConfigurationSource propertiesFileConfigSource = new PropertiesFileConfigurationSource();
        propertiesFileConfigSource.setResources(Arrays.asList(
                "application.properties",
                "/common.properties",
                "conf/application.properties",
                environment.getProperty("env") + "/application.properties",
                "/etc/isignthis/application.properties",
                environment.getProperty("user.home") + "/isignthis.properties",
                configForTestRun())
        );

        return propertiesFileConfigSource;
    }

    // if we running tests load also the -test profile properties
    private String configForTestRun() {
        environment.getActiveProfiles();
        if (environment.getActiveProfiles().length > 0 && "test".equals(environment.getActiveProfiles()[0])) {
            return "application-test.properties";
        }
        return "should-not-exist.skip";
    }
}
