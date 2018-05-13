package tech.rsqn.cdsl;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.rsqn.cdsl.context.CdslContextRepository;
import tech.rsqn.cdsl.execution.FlowExecutor;
import tech.rsqn.cdsl.registry.FlowRegistry;

import java.io.IOException;

public abstract class CdslAlexaSupport {

}
/*
@Component
public abstract class CdslAlexaSupport<C, R> extends AbstractDynamoBackedService implements RequestHandler<APIGatewayProxyRequestEvent, R> {

    private static final Logger LOG = Logger.getLogger(CdslLambdaSupport.class);

    @Autowired
    protected FlowRegistry flowRegistry;

    @Autowired
    protected FlowExecutor executor;

    @Autowired
    protected CdslContextRepository contextRepository;


    public CdslLambdaSupport() {
        BasicConfigurator.configure();
    }

    public abstract R handleCdslRequest(APIGatewayProxyRequestEvent proxyEvent, C model, Context context);

    public abstract Class getModelClass();

    public R handleRequest(APIGatewayProxyRequestEvent proxyEvent, Context context) {
        ObjectMapper objectMapper = new ObjectMapper();
        C model = null;

        try {
            if (proxyEvent.getBody() != null && proxyEvent.getBody().length() > 0) {
                model = (C) objectMapper.readValue(proxyEvent.getBody(), getModelClass());
            }

            return this.handleCdslRequest(proxyEvent, model, context);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
*/