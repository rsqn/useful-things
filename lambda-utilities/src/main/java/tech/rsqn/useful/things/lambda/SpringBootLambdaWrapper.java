package tech.rsqn.useful.things.lambda;


import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tech.rsqn.useful.things.lambda.model.ApiGatewayResponse;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is intended for use in development of java lambda functions, allowing you to run them locally as spring boot.
 */

public abstract class SpringBootLambdaWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBootLambdaWrapper.class);

    protected <T> ResponseEntity handleRequest(HttpServletRequest req, HttpServletResponse resp, T model, RequestHandler lambdaHandler) {
        return handleRequest(req, resp, model, lambdaHandler, new HashMap<>());
    }

    protected <T> ResponseEntity handleRequest(HttpServletRequest req, HttpServletResponse resp, T model, RequestHandler lambdaHandler, Map<String, String> pathVariables) {
        ObjectMapper objectMapper = new ObjectMapper();
        APIGatewayProxyRequestEvent lambdaEvent = new APIGatewayProxyRequestEvent();

        // headers
        HashMap<String, String> hdrs = new HashMap<String, String>();
        Enumeration<String> hdrEn = req.getHeaderNames();
        while (hdrEn.hasMoreElements()) {
            String n = hdrEn.nextElement();
            hdrs.put(n, req.getHeader(n));
        }

        // query string
        HashMap<String, String> qs = new HashMap<String, String>();
        Enumeration<String> qsEn = req.getParameterNames();
        while (qsEn.hasMoreElements()) {
            String n = qsEn.nextElement();
            qs.put(n, req.getParameter(n));
        }

        // body
        if (model != null) {
            try {
                lambdaEvent.setBody(objectMapper.writeValueAsString(model));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        lambdaEvent.setHeaders(hdrs);
        lambdaEvent.setQueryStringParameters(qs);
        lambdaEvent.setHttpMethod(req.getMethod());
        lambdaEvent.setPath(req.getRequestURI());
        lambdaEvent.setPathParameters(pathVariables);

        ApiGatewayResponse ret = (ApiGatewayResponse) lambdaHandler.handleRequest(lambdaEvent, makeContext());

        if (ret.getStatusCode() == 302) {
            try {
                resp.sendRedirect(ret.getHeaders().get("Location"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        } else {

            HttpHeaders h = new HttpHeaders();
            for (String n : ret.getHeaders().keySet()) {
                h.add(n, hdrs.get(n));
            }
            return new ResponseEntity<Object>(ret._getObjectBody(), h, HttpStatus.valueOf(ret.getStatusCode()));
        }
    }

    private Context makeContext() {
        return new Context() {
            public String getAwsRequestId() {
                return "mock";
            }

            public String getLogGroupName() {
                return null;
            }

            public String getLogStreamName() {
                return null;
            }

            public String getFunctionName() {
                return null;
            }

            public String getFunctionVersion() {
                return null;
            }

            public String getInvokedFunctionArn() {
                return null;
            }

            public CognitoIdentity getIdentity() {
                return null;
            }

            public ClientContext getClientContext() {
                return null;
            }

            public int getRemainingTimeInMillis() {
                return 0;
            }

            public int getMemoryLimitInMB() {
                return 512;
            }

            public LambdaLogger getLogger() {
                return new LambdaLogger() {
                    public void log(String s) {
                        LOG.info(s);
                    }

                    @Override
                    public void log(byte[] bytes) {
                        LOG.info(new String(bytes, StandardCharsets.UTF_8));
                    }
                };
            }
        };
    }

}