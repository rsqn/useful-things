package tech.rsqn.useful.things.lambda.model;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestDto {
    private String resource;
    private String path;
    private String httpMethod;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryStringParameters = new HashMap<>();
    private Map<String, String> pathParameters = new HashMap<>();
    private Map<String, String> stageVariables = new HashMap<>();
    private String Body;
    private List<String> cookies;

    private String version;
    private String routeKey;
    private String rawPath;
    private String rawQueryString;



    public HttpRequestDto with(APIGatewayV2HTTPEvent evt) {
        BeanUtils.copyProperties(evt,this);
        path = evt.getRawPath();
        httpMethod = evt.getRequestContext().getHttp().getMethod();
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

    public String getBody() {
        return Body;
    }

    public void setBody(String body) {
        Body = body;
    }

    public List<String> getCookies() {
        return cookies;
    }

    public void setCookies(List<String> cookies) {
        this.cookies = cookies;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public void setRouteKey(String routeKey) {
        this.routeKey = routeKey;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getRawQueryString() {
        return rawQueryString;
    }

    public void setRawQueryString(String rawQueryString) {
        this.rawQueryString = rawQueryString;
    }

    @Override
    public String toString() {
        return "HttpRequestDto{" +
                "resource='" + resource + '\'' +
                ", path='" + path + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", headers=" + headers +
                ", queryStringParameters=" + queryStringParameters +
                ", pathParameters=" + pathParameters +
                ", stageVariables=" + stageVariables +
                ", Body='" + Body + '\'' +
                ", cookies=" + cookies +
                ", version='" + version + '\'' +
                ", routeKey='" + routeKey + '\'' +
                ", rawPath='" + rawPath + '\'' +
                ", rawQueryString='" + rawQueryString + '\'' +
                '}';
    }
}
