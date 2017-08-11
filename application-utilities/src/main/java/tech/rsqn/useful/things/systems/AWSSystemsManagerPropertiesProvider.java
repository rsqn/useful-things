package tech.rsqn.useful.things.systems;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.util.StringUtils;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class AWSSystemsManagerPropertiesProvider implements InitializingBean {
    private Logger log = LoggerFactory.getLogger(AWSSystemsManagerPropertiesProvider.class);

    private AWSSimpleSystemsManagementClient ssmClient;

    private String parameterPrefix;

    private boolean logParameterValues = false;

    private Map<String, String> allProperties = new HashMap<>();
    private Map<String, String> defaultProperties = new HashMap<>();
    private List<String> resolvedProperties = new ArrayList<>();

    public void setDefaultProperties(Map<String, String> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    private List<String> names = new ArrayList<>();

    public AWSSystemsManagerPropertiesProvider() {
        parameterPrefix = "";
    }


    public void setLogParameterValues(boolean logParameterValues) {
        this.logParameterValues = logParameterValues;
    }

    public void setParameterPrefix(String parameterPrefix) {
        this.parameterPrefix = parameterPrefix;
    }

    @Required
    public void setSsmClient(AWSSimpleSystemsManagementClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    @Required
    public void setNames(List<String> names) {
        this.names = names;
    }

    public void resolveAllParameters() {

        List<String> allNames = new ArrayList<>();

        for (String name : names) {
            allNames.add(name);
            if (!StringUtils.isNullOrEmpty(parameterPrefix)) {
                name = parameterPrefix + name;
                allNames.add(name);
            }
        }

        Iterable<List<String>> partitions = Iterables.partition(allNames, 10);

        for (List<String> partition : partitions) {
            GetParametersRequest request = new GetParametersRequest();

            List<String> filtered = partition.stream()
                    .filter(k -> {
                        if (k.startsWith("aws.")) {
                            log.warn("Will not try to resolve unprefixed key (" + k + ") - AWS does not allow this");
                            return false;
                        }
                        return true;
                    }).collect(Collectors.toList());

            request.setNames(filtered);
            request.setWithDecryption(true);

            GetParametersResult result = ssmClient.getParameters(request);

            for (Parameter parameter : result.getParameters()) {
                String n = parameter.getName();
                String v = parameter.getValue();

                if (!StringUtils.isNullOrEmpty(parameterPrefix)) {
                    if (n.startsWith(parameterPrefix)) {
                        log.info("removing environment prefix " + parameterPrefix + " from parameter name " + n);
                        n = n.substring(parameterPrefix.length());
                    }
                }

                String logValue = "########";
                if (logParameterValues) {
                    logValue = v;
                }

                log.info("Parameter (" + n + ") = (" + logValue + ") in spring context ");
                allProperties.put(n, v);
            }
        }
    }


    //todo - ensure prefixe parameter takes precedence
    private String forceResolve(String key) {
        GetParametersRequest request;

        if (key.startsWith("aws.")) {
            log.warn("Will not try to resolve unprefixed key (" + key + ") - AWS does not allow this");
            if (org.apache.commons.lang3.StringUtils.isNotEmpty(parameterPrefix)) {
                request = new GetParametersRequest().withNames(parameterPrefix + key).withWithDecryption(true);
            } else {
                return null;
            }
        } else {
            request = new GetParametersRequest().withNames(parameterPrefix + key, key).withWithDecryption(true);
        }

        GetParametersResult result = ssmClient.getParameters(request);

        for (Parameter parameter : result.getParameters()) {
            return parameter.getValue();
        }
        return null;
    }

    public String resolve(String key) {
        String v = allProperties.get(key);

        if (v == null && !resolvedProperties.contains(key)) {
            v = forceResolve(key);
            if (v != null) {
                allProperties.put(key, v);
            }
            resolvedProperties.add(key);
        }

        if (org.apache.commons.lang3.StringUtils.isEmpty(v)) {
            v = defaultProperties.get(key);
            if (org.apache.commons.lang3.StringUtils.isEmpty(v)) {
                log.info("Parameter resolving (" + key + ") - no value or default present ");
            } else {
                log.info("Parameter (" + key + ") - default used ");
            }
        } else {
            log.info("Parameter resolved (" + key + ") present ? " + org.apache.commons.lang3.StringUtils.isNotEmpty(v));
        }

        if (v.startsWith("${")) {
            log.info("Parameter null for (" + key + ") as it appears to be an unresolved placeholder");
            v = null;
        } else {
            log.info("Parameter (" + key + ") passed placeholder filter");

        }

        return v;
    }

    public String resolve(String key, String _default) {
        String v = allProperties.get(key);

        if (org.apache.commons.lang3.StringUtils.isEmpty(v)) {
            v = _default;
            log.info("Resolving parameter (" + key + ") - default used ");

        } else {
            log.info("Resolving parameter (" + key + ") present ? " + org.apache.commons.lang3.StringUtils.isNotEmpty(v));
        }

        return v;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        resolveAllParameters();
    }

}
