package tech.rsqn.useful.things.configuration;


import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This should really only be used for model.
 */
public class PropertiesFileConfigurationSource extends AbstractConfigurationSource {
    private Map<String,String> used = new ConcurrentHashMap();

	Logger log = LoggerFactory.getLogger(PropertiesFileConfigurationSource.class);
    Properties myProps;
    private List<String> resources = new ArrayList();

    public void setResources(List<String> resources) {
        this.resources = resources;
    }

    @Override
    public String getEnvironment() {
        return System.getProperty("env");
    }

    public PropertiesFileConfigurationSource(String name) {
        if (resources == null) {
            resources = new ArrayList<>();
        }
        resources.add(name);
        init();
    }

    public PropertiesFileConfigurationSource() {
    }

    private void reportOnProperties() {
        List<String> notUsed = new ArrayList<>();

        for (Object o : myProps.keySet()) {
            String k = (String)o;
            if ( ! used.containsKey(k)) {
                notUsed.add(k);
            }
        }

        for (String k : used.keySet()) {
            log.info("PROPERTY IS USED: " + k);
        }

        for (String k : notUsed) {
            log.warn("PROPERTY IS NOT USED: " + k);
        }
    }


    public void init() {
        try {
            myProps = new Properties();
            int ctr = 0;


            for (String rn : resources) {
                loadResource(rn, ctr == 0);
                ctr++;
            }

        } catch (Exception ex) {
            log.error("Exception loading properties " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void loadResource(String name, boolean throwErrorIfNotFound) {
        Resource resource = new ClassPathResource(getEnvironment() + "/" + name);

        if (!resource.exists()) {
            resource = new ClassPathResource(name);
        }

        if (!resource.exists()) {
            if (throwErrorIfNotFound) {
                throw new RuntimeException("Resource " + name + " was not found");
            } else {
                log.warn("Resource " + name + " does not exist in classpath - checking file system");

                File f = new File(name);
                if (!f.exists()) {
                    log.warn("Resource " + name + " does not exist in classpath or filesystem - bypassing");
                    return;
                } else {
                    log.warn("Resource " + name + " was found on filesystem");
                    resource = new FileSystemResource(f);
                }
            }
        } else {
            log.warn("Resource " + name + " was found in classpath");

        }

        log.info("Loading properties from " + resource.getFilename());

        Properties properties = new Properties();
        try {
            InputStreamReader unicodeIsr = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            properties.load(unicodeIsr);
            for (Object o : properties.keySet()) {
                String k = o.toString();
                if (myProps.get(o) != null) {
                    log.info("OVERRIDE property " + k + " from " + name);
                } else {
                    log.info("SET property " + k + " from " + name);
                }
                myProps.put(k, properties.get(o));

                if (k.startsWith("sysprop.")) {
                    String n = k.substring(8);
                    log.info("SET SYSPROP " + n + " from " + k);
                    System.setProperty(n, myProps.getProperty(k));
                }
                Object value = properties.get(o);
                myProps.put(o, value);
            }
        } catch (IOException e) {
            throw new ConfigurationRuntimeException(e);
        }
    }

	public static PropertiesFileConfigurationSource withClassPathResource(String name) {
        PropertiesFileConfigurationSource ret = new PropertiesFileConfigurationSource(name);
        return ret;
    }

    public String getStringValue(String name) {
        used.put(name,name);
        return myProps.getProperty(name);
    }

    @Override
    public String getStringValue(String name, String dfl) {
        used.put(name,name);
        String ret = myProps.getProperty(name);
        if (Strings.isNullOrEmpty(ret)) {
            ret = dfl;
        }
        return ret;
    }

    public Map<String, String> asMap() {
        Map<String, String> ret = new HashMap<String, String>();

        for (Object o : myProps.keySet()) {
            ret.put((String) o, myProps.getProperty((String) o));
        }
        return ret;
    }

    @Override
    public List<String> getNames() {
        List<String> ret = new ArrayList();
        ret.addAll(myProps.stringPropertyNames());
        return ret;
    }

    @Override
    public void setValue(String name, String value) {
        myProps.put(name, value);
    }


}
