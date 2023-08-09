package tech.rsqn.useful.things.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import tech.rsqn.useful.things.lambda.exceptions.ErrorCode;
import tech.rsqn.useful.things.lambda.model.ApiGatewayResponse;
import tech.rsqn.useful.things.lambda.model.HttpRequestDto;
import tech.rsqn.useful.things.lambda.model.HttpResponseDto;

import java.util.Date;
import java.util.Map;

public abstract class AbstractLambdaFunctionRouter implements RequestHandler<APIGatewayV2HTTPEvent, ApiGatewayResponse> {
    private static Logger LOG = LoggerFactory.getLogger(AbstractLambdaFunctionRouter.class);

    @Autowired
    private ApplicationContext applicationContext;

    protected abstract Map<String,Class> getMappings();

    private AbstractHttpFunction resolve(APIGatewayV2HTTPEvent req) {
        String key = req.getRawPath();
        LOG.debug("Resolving handler for " + key);
        Class functionClass = getMappings().get(key);

        if (functionClass != null) {
            LOG.debug("Resolved function for " + key + " to " + functionClass.getClass().getSimpleName());
            return resolveBean(functionClass);
        } else {
            LOG.warn("Unable to resolve function for " + key);
            throw new RuntimeException("Unable to find function for " + key);
        }
    }


    private AbstractHttpFunction resolveBean(Class c) {
        AbstractHttpFunction function = null;
        if ( applicationContext != null) {
            function = (AbstractHttpFunction) applicationContext.getBean(c);
            if (function == null) {
                LOG.warn("Unable to find bean for class " + c.getSimpleName());
                throw new RuntimeException("Unable to find bean for class " + c.getSimpleName());
            }
            return function;
        }
        applicationContext = new AnnotationConfigApplicationContext(c);
        return resolveBean(c);
    }


    private ApiGatewayResponse callFunction(APIGatewayV2HTTPEvent event, Context context) {
        HttpResponseDto responseDto = null;
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        objectMapper.setDateFormat(new ISO8601DateFormat());

//            if (proxyEvent.getBody() != null && proxyEvent.getBody().length() > 0) {
//                model = (C) objectMapper.readValue(proxyEvent.getBody(), getModelClass());
//            }
        AbstractHttpFunction function = resolve(event);
        try {
            Object v = function.handle(new HttpRequestDto().with(event), context);
            if ( v instanceof HttpResponseDto) {
                responseDto = (HttpResponseDto) v;
                LOG.info("got a response DTO " + responseDto);
            } else {
                responseDto = new HttpResponseDto().ok(v);
                LOG.info("got a value - so making DTO " + responseDto);
            }
        } catch ( ErrorCode ec ) {
            responseDto = new HttpResponseDto().status(ec.getCode(),ec.getMessage());
            LOG.warn("Got an errorCode so making DTO " + responseDto);
        } catch ( Exception ex ) {
            LOG.warn(ex.getMessage(),ex);
            responseDto = new HttpResponseDto().status(500).error(ex.getMessage());
            LOG.warn("Got an Exception so making DTO " + responseDto);

        }
        return responseDto.toResponse();
    }

    public ApiGatewayResponse handleRequest(APIGatewayV2HTTPEvent httpEvent, Context context) {
        try {
            LOG.debug("ProxyRequest " + httpEvent.toString());
            if ( httpEvent.getHeaders() == null && httpEvent.getRequestContext().getHttp().getMethod() == null) {
                LOG.debug("v2 This seems to be a keepalive. returning");
                return null;
            }
            if ( "GET".equals(httpEvent.getRequestContext().getHttp().getMethod())) {
                if ( httpEvent.getQueryStringParameters() != null ) {
                    if ( "true".equals(httpEvent.getQueryStringParameters().get("ping"))) {
                        LOG.debug("v2 This seems to be a ping. returning an OK");
                        return ApiGatewayResponse.builder()
                                .withNoCache()
                                .setStatusCode(HttpStatus.OK.value())
                                .setRawBody(new Date().toString())
                                .build();
                    }
                }
            }
            return this.callFunction(httpEvent, context);
        } catch (Exception e) {
            LOG.error(e.getMessage(),e);
            throw new RuntimeException(e);
        }
    }
}
