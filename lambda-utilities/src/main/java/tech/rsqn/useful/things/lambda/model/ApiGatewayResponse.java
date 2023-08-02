package tech.rsqn.useful.things.lambda.model;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ApiGatewayResponse {

    private int statusCode;
    private String body;
    private Map<String, String> headers = new HashMap<>();
    private boolean isBase64Encoded;
    private Object objectBody;


    public ApiGatewayResponse(int statusCode, String body, Map<String, String> headers, boolean isBase64Encoded) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
        this.isBase64Encoded = isBase64Encoded;
    }

    public Object _getObjectBody() {
        return objectBody;
    }

    public void setObjectBody(Object objectBody) {
        this.objectBody = objectBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public boolean isIsBase64Encoded() {
        return isBase64Encoded;
    }

    public static Builder builder() {
        Builder builder = new Builder();
        return builder.withNoCache();
    }

    public static class Builder {

        private static final Logger LOG = LoggerFactory.getLogger(ApiGatewayResponse.Builder.class);

        private static final ObjectMapper objectMapper = new ObjectMapper();

        private int statusCode = 200;
        private Map<String, String> headers = new HashMap<>();
        private String rawBody;
        private Object objectBody;
        private byte[] binaryBody;
        private boolean base64Encoded;

        public Builder setStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder setHeaders(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder withNoCache() {
            headers.put("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
            headers.put("Pragma", "no-cache"); // HTTP 1.0.
            headers.put("Expires", "0"); // Proxies.
            return this;
        }


        public Builder allowCaching() {
            headers.remove("Cache-Control");
            headers.remove("Pragma");
            headers.remove("Expires");
            return this;
        }

        /**
         * Builds the {@link ApiGatewayResponse} using the passed raw body string.
         */
        public Builder setRawBody(String rawBody) {
            this.rawBody = rawBody;
            return this;
        }


        /**
         * Builds the {@link ApiGatewayResponse} using the passed object body
         * converted to JSON.
         */
        public Builder setObjectBody(Object objectBody) {
            this.objectBody = objectBody;
            return this;
        }

        public Builder setRedirect(String url) {
            this.statusCode = 302;
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.put("Location", url);
            return this;
        }

        /**
         * Builds the {@link ApiGatewayResponse} using the passed binary body
         * encoded as base64. {@link #setBase64Encoded(boolean)
         * setBase64Encoded(true)} will be in invoked automatically.
         */
        public Builder setBinaryBody(byte[] binaryBody) {
            this.binaryBody = binaryBody;
            setBase64Encoded(true);
            return this;
        }

        /**
         * A binary or rather a base64encoded responses requires
         * <ol>
         * <li>"Binary Media Types" to be configured in API Gateway
         * <li>a request with an "Accept" header set to one of the "Binary Media
         * Types"
         * </ol>
         */
        public Builder setBase64Encoded(boolean base64Encoded) {
            this.base64Encoded = base64Encoded;
            return this;
        }


        public ApiGatewayResponse build() {
            String body = null;
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.setDateFormat(new ISO8601DateFormat());

            if (rawBody != null) {
                body = rawBody;
            } else if (objectBody != null) {
                try {
                    body = objectMapper.writeValueAsString(objectBody);
                    setObjectBody(objectBody);
                } catch (JsonProcessingException e) {
                    LOG.error("failed to serialize object", e);
                    throw new RuntimeException(e);
                }
            } else if (binaryBody != null) {
                body = new String(Base64.getEncoder().encode(binaryBody), StandardCharsets.UTF_8);
            }
            ApiGatewayResponse ret = new ApiGatewayResponse(statusCode, body, headers, base64Encoded);
            ret.setObjectBody(body);
            return ret;
        }
    }
}

