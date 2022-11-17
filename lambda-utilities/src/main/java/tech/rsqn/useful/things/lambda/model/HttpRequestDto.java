package tech.rsqn.useful.things.lambda.model;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class HttpRequestDto {
    private String resource;
    private String path;
    private String httpMethod;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryStringParameters = new HashMap<>();
    private Map<String, String> pathParameters = new HashMap<>();
    private Map<String, String> stageVariables = new HashMap<>();
    private APIGatewayProxyRequestEvent.ProxyRequestContext requestContext;
    private String body;

    public HttpRequestDto with(APIGatewayProxyRequestEvent evt) {
        BeanUtils.copyProperties(evt,this);
        return this;
    }

    public String getParameter(String key) {
        String s = queryStringParameters.get(key);
        if (StringUtils.hasText(s)) {
            return s;
        }
        return null;
    }

    public String getHeader(String key) {
        String s = headers.get(key);
        if (StringUtils.hasText(s)) {
            return s;
        }
        return null;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQueryStringParameters() {
        return queryStringParameters;
    }

    public void setQueryStringParameters(Map<String, String> queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    public Map<String, String> getStageVariables() {
        return stageVariables;
    }

    public void setStageVariables(Map<String, String> stageVariables) {
        this.stageVariables = stageVariables;
    }

    public APIGatewayProxyRequestEvent.ProxyRequestContext getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(APIGatewayProxyRequestEvent.ProxyRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
