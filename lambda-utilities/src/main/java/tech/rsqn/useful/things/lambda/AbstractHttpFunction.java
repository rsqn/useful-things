package tech.rsqn.useful.things.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.rsqn.useful.things.lambda.exceptions.ErrorCode;
import tech.rsqn.useful.things.lambda.model.ApiGatewayResponse;
import tech.rsqn.useful.things.lambda.model.HttpRequestDto;
import tech.rsqn.useful.things.lambda.model.HttpResponseDto;

public abstract class AbstractHttpFunction<C,R> extends AbstractLambdaSpringService<C, ApiGatewayResponse> {
    private static Logger LOG = LoggerFactory.getLogger(AbstractHttpFunction.class);

    public abstract R handle(HttpRequestDto dto, C c);

    @Override
    public Class getModelClass() {
        return Object.class;
    }

    @Override
    public ApiGatewayResponse handleRequest(APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, C c, Context context) {
        HttpResponseDto responseDto = null;

        try {
            Object v = handle(new HttpRequestDto().with(apiGatewayProxyRequestEvent), c);
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

}
