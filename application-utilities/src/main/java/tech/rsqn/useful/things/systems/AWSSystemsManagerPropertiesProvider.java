package tech.rsqn.useful.things.systems;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.util.StringUtils;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AWSSystemsManagerPropertiesProvider implements ApplicationContextAware, InitializingBean, BeanFactoryPostProcessor {

    private Logger log = LoggerFactory.getLogger(AWSSystemsManagerPropertiesProvider.class);

    private ApplicationContext applicationContext;
    private ConfigurableListableBeanFactory beanFactory;

    private AWSSimpleSystemsManagementClient ssmClient;

    private String parameterPrefix;

    private boolean logParameterValues = false;

    private Map<String, String> allProperties = new HashMap<>();

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

        Iterable<List<String>> partitions = Iterables.partition(allNames,10);

        for (List<String> partition : partitions) {
            GetParametersRequest request = new GetParametersRequest();
            request.setNames(partition);
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

                log.info("Setting parameter (" + n + ") = (" + logValue + ") in spring context ");
                allProperties.put(n, v);

                /**
                 * I'm sure this is NOT the way to do it - but it works nicely.
                 */
                beanFactory.registerSingleton(n, v);
            }
        }
    }

    public String resolve(String key) {
        return allProperties.get(key);
    }


    @Override
    public void afterPropertiesSet() throws Exception {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        resolveAllParameters();

    }
}
