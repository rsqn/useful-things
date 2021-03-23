package tech.rsqn.useful.things.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.springframework.http.HttpStatus;
import tech.rsqn.useful.things.lambda.model.ApiGatewayResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;


public abstract class AbstractLambdaSpringService<C, R> implements RequestHandler<APIGatewayProxyRequestEvent, R> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLambdaSpringService.class);

    public AbstractLambdaSpringService() {
        //BasicConfigurator.configure();
    }

    protected void wire(Object o) {
        LambdaSpringUtil.wireInSpring(o, o.getClass().getSimpleName());
    }

    public abstract R handleRequest(APIGatewayProxyRequestEvent proxyEvent, C model, Context context);

    public abstract Class getModelClass();

    public R handleRequest(APIGatewayProxyRequestEvent proxyEvent, Context context) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setDateFormat(new ISO8601DateFormat());

        // Lambda is supposed to magically do this - but didnt seem to work for me after a few go's
        C model = null;
        try {
            LOG.debug("ProxyRequest " + proxyEvent.toString());
            if (proxyEvent.getHeaders() == null && proxyEvent.getHttpMethod() == null) {
                LOG.debug("v2 This seems to be a keepalive. returning");
                return null;
            }
            if ("GET".equals(proxyEvent.getHttpMethod())) {
                if (proxyEvent.getQueryStringParameters() != null) {
                    if ("true".equals(proxyEvent.getQueryStringParameters().get("ping"))) {
                        LOG.debug("v2 This seems to be a ping. returning an OK");
                        return (R) ApiGatewayResponse.builder()
                                .withNoCache()
                                .setStatusCode(HttpStatus.OK.value())
                                .setRawBody(new Date().toString())
                                .build();
                    }
                }
            }

            if (proxyEvent.getBody() != null && proxyEvent.getBody().length() > 0) {
                model = (C) objectMapper.readValue(proxyEvent.getBody(), getModelClass());
            }

            Object r = this.handleRequest(proxyEvent, model, context);

            if (r == null) {
                LOG.warn("Return value is null - this will likely fail");
            } else if (!(r instanceof ApiGatewayResponse)) {
                LOG.warn("Return type is not an ApiGatewayResponse, it is a " + r.getClass() + "  - this will likely fail");
            }

            return (R) r;
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            LOG.info("after execute");
        }
    }


}
