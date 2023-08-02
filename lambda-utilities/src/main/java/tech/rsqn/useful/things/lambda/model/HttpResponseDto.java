package tech.rsqn.useful.things.lambda.model;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class HttpResponseDto {
    private int status;
    private String body;
    private Map<String, String> headers = new HashMap<>();
    private Object objectBody;
    private String redirect;

    public HttpResponseDto ok() {
        this.status = 200;
        return this;
    }

    public HttpResponseDto ok(Object b) {
        this.status = 200;
        this.body(b);
        return this;
    }

    public HttpResponseDto status(int status) {
        this.status = status;
        return this;
    }

    public HttpResponseDto status(int status,Object b) {
        this.status = status;
        this.body(b);
        return this;
    }

    public HttpResponseDto error(Object b) {
        this.status = 500;
        this.body(b);
        return this;
    }

    public HttpResponseDto redirect(String r) {
        this.redirect = r;
        this.status = 307;
        return this;
    }

    public HttpResponseDto body(Object body) {
        if ( body instanceof String ) {
            this.body = (String)body;
        } else {
            this.objectBody = body;
        }
        return this;
    }

    public HttpResponseDto header(String k, String v) {
        this.headers.put(k,v);
        return this;
    }

    public HttpResponseDto headers(Map<String,String> map) {
        this.headers.putAll(map);
        return this;
    }


    public ApiGatewayResponse toResponse() {
        ApiGatewayResponse.Builder builder = ApiGatewayResponse.builder()
                .setStatusCode(status)
                .setHeaders(headers);

        if (StringUtils.hasText(redirect)) {
            builder.setRedirect(redirect);
        }

        if (StringUtils.hasText(body)) {
            builder.setRawBody(body);
        }

        if (objectBody != null) {
            builder.setObjectBody(objectBody);
        }

        return builder.build();
    }



    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Object getObjectBody() {
        return objectBody;
    }

    public void setObjectBody(Object objectBody) {
        this.objectBody = objectBody;
    }

    @Override
    public String toString() {
        return "HttpResponseDto{" +
                "status=" + status +
                ", body='" + body + '\'' +
                ", headers=" + headers +
                ", objectBody=" + objectBody +
                ", redirect='" + redirect + '\'' +
                '}';
    }
}
