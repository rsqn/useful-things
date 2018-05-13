package tech.rsqn.useful.things.web;

import org.apache.http.*;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 11/19/12
 */
public class WebClient {
    static Logger log = LoggerFactory.getLogger(WebClient.class);
    private static CloseableHttpClient instance = null;
    private static Object lock = new Object();

    private List<Header> headers = new ArrayList<>();
    private String url;
    private WebResponseHandler responseHandler;
    private byte[] binaryBody;
    private String body;
    private String httpType;
    private String charset = "UTF-8";
    private String contentType;
    private Integer connectTimeoutMs = null;
    private Integer connectRequestTimeoutMs = null;
    private Integer socketTimeoutMs = null;
    private SSLConnectionSocketFactory alternateSSLSocketFactory = null;
    private HttpHost proxy;
    private ResponseHandler directResponseHandler;

    public static WebClient get(String url) {
        WebClient ret = new WebClient();
        ret.url = url;
        ret.httpType = "GET";
        return ret;
    }

    public static WebClient options(String url) {
        WebClient ret = new WebClient();
        ret.url = url;
        ret.httpType = "OPTIONS";
        return ret;
    }


    public static WebClient post(String url) {
        WebClient ret = new WebClient();
        ret.url = url;
        ret.httpType = "POST";
        return ret;
    }

    public static WebClient put(String url) {
        WebClient ret = new WebClient();
        ret.url = url;
        ret.httpType = "PUT";
        return ret;
    }

    public static WebClient delete(String url) {
        WebClient ret = new WebClient();
        ret.url = url;
        ret.httpType = "DELETE";
        return ret;
    }

    public WebClient andHeader(String name, String value) {
        BasicHeader header = new BasicHeader(name, value);
        headers.add(header);
        return this;
    }

    public WebClient andCharset(String s) {
        this.charset = s;
        return this;
    }

    public WebClient andUniqueHeader(String name, String value) {
        Iterator<Header> it = headers.iterator();
        while (it.hasNext()) {
            Header hdr = it.next();
            if (hdr.getName().equals(name)) {
                it.remove();
            }
        }

        andHeader(name, value);
        return this;
    }

    public WebClient andRemoveHeader(String name) {
        Iterator<Header> it = headers.iterator();
        while (it.hasNext()) {
            Header hdr = it.next();
            if (hdr.getName().equals(name)) {
                it.remove();
            }
        }

        return this;
    }

    public WebClient andAuthorization(String value) {
        BasicHeader header = new BasicHeader("Authorization", value);
        headers.add(header);
        return this;
    }

    public WebClient andProxy(String host, int port) {
        this.proxy = new HttpHost(host, port, "http");
        return this;
    }


    public WebClient andJsonResponse() {
        andHeader("Accept", "application/json");
        responseHandler = new JsonObjectResponseHandler();
        return this;
    }

    public WebClient andDirectResponseHandler(ResponseHandler handler) {
        directResponseHandler = handler;
        return this;
    }

    public <T> WebClient andJsonResponse(final Class t) {
        andHeader("Accept", "application/json");
        responseHandler = new WebResponseHandler<T>() {

            @Override
            public T handleResponse(String responseBody) {
                try {
//                    return (T) jsonUtils.fromJSON(t, responseBody);
                    return null;
                } catch (Exception ex) {
                    throw new RuntimeException("Exception processing " + responseBody);
                }
            }
        };
        return this;
    }

    public WebClient andResponseHandler(WebResponseHandler handler) {
        this.responseHandler = handler;
        return this;
    }

    public WebClient andTextHtmlResponse() {
        andHeader("Accept", "text/html");
        responseHandler = new TextHtmlResponseHandler();
        return this;
    }

    public WebClient andTextPlainResponse() {
        andHeader("Accept", "text/plain");
        responseHandler = new TextPlainResponseHandler();
        return this;
    }

    public WebClient andContentType(String t) {
        contentType = t;
        return this;
    }

    public WebClient andJsonBody(String json) {
        body = json;
        contentType = "application/json";
        return this;
    }

    public WebClient andBody(String body) {
//        contentType = "application/json";
        this.body = body;
        return this;
    }

    public WebClient andBinaryBody(byte[] binaryBody) {
        contentType = "application/octet-stream";
        this.binaryBody = binaryBody;
        return this;
    }

    public WebClient andFormBody(String formBody) {
        body = formBody;
        contentType = "application/x-www-form-urlencoded";
        return this;
    }

    /**
     * Builder method to set the Connection Request Timeout (i.e. time to get connection from connection pool)
     *
     * @param timeout - timeout in milliseconds
     * @return
     */
    public WebClient andConnectRequestTimeoutMs(int timeout) {
        connectRequestTimeoutMs = timeout;
        return this;
    }


    /**
     * Builder method to set the Connection Timeout (i.e. time for handshake)
     *
     * @param timeout - timeout in milliseconds
     * @return
     */
    public WebClient andConnectTimeoutMs(int timeout) {
        connectTimeoutMs = timeout;
        return this;
    }

    /**
     * Builder method to set the Socket Timeout (i.e. data transfer time)
     *
     * @param timeout - timeout in milliseconds
     * @return
     */
    public WebClient andSocketTimeoutMs(int timeout) {
        socketTimeoutMs = timeout;
        return this;
    }

    /**
     * Builder method to set the Connection Request Timeout, Connection Timeout & Socket Timeout
     *
     * @param timeout - timeout in milliseconds
     * @return
     */
    public WebClient andTimeoutMs(int timeout) {
        connectRequestTimeoutMs = timeout;
        connectTimeoutMs = timeout;
        socketTimeoutMs = timeout;
        return this;
    }

    public <T> T perform() {
        try {
            switch (httpType) {
                case "GET":
                    return performGet();
                case "POST":
                    return performPost();
                case "PUT":
                    return performPut();
                case "DELETE":
                    return performDelete();
                case "OPTIONS":
                    return performOptions();
            }
        } catch (Exception e) {
            throw new RuntimeException("Exception executing request " + this + " - " + e, e);
        }
        return null;
    }


    /**
     * Finalize and execute HTTP request, based on client configuration, retrieve HTTP response details
     * (including deserialized POJO based on configuration) wrapped in a generic client response.
     *
     * @param <T> The class type the HTTP response body should be mapped to.
     * @return A generic response which includes HTTP response details, including the deserialized response body.
     */
    public <T> WebClientResponse<T> tryPerform() //todo a more suitable method name?
    {
        WebClientResponse res = new WebClientResponse();

        try {
            CloseableHttpClient httpClient = getHttpClient();

            HttpResponse httpResponse = null;

            switch (httpType) {
                case "GET": {
                    HttpGet httpGet = new HttpGet(url);
                    headers.forEach(httpGet::addHeader);

                    httpResponse = httpClient.execute(httpGet);
                }
                break;
                case "POST": {

                    HttpPost httpPost = new HttpPost(url);
                    headers.forEach(httpPost::addHeader);
                    StringEntity stringEntity = new StringEntity(body, charset);
                    stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, contentType));
                    httpPost.setEntity(stringEntity);

                    httpResponse = httpClient.execute(httpPost);
                }
                break;
                case "PUT": {
                    HttpPut httpPut = new HttpPut(url);
                    headers.forEach(httpPut::addHeader);
                    StringEntity stringEntity = new StringEntity(body, charset);
                    stringEntity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, contentType));

                    httpResponse = httpClient.execute(httpPut);
                }
                break;
                case "DELETE": {
                    HttpDelete httpDelete = new HttpDelete(url);
                    headers.forEach(httpDelete::addHeader);

                    httpResponse = httpClient.execute(httpDelete);
                }
                break;
                case "OPTIONS": {
                    HttpOptions httpOptions = new HttpOptions(url);
                    headers.forEach(httpOptions::addHeader);

                    httpResponse = httpClient.execute(httpOptions);
                }
                break;
                default: {
                    throw new RuntimeException("HTTP request type '" + httpType + "' not supported.");
                }
            }

            if (httpResponse == null) {
                throw new RuntimeException("No HTTP response?");
            }

            StatusLine statusLine = httpResponse.getStatusLine();
            ProtocolVersion protocolVersion = httpResponse.getProtocolVersion();
            HttpEntity httpEntity = httpResponse.getEntity();
            String stringResponse = EntityUtils.toString(httpEntity);
            ContentType httpEntityContentType = ContentType.getOrDefault(httpEntity);
            String responseContentType = httpEntityContentType != null ? httpEntityContentType.getMimeType() : null;
            Charset httpEntityContentTypeCharset = httpEntityContentType != null ? httpEntityContentType.getCharset() : null;
            String responseCharset = httpEntityContentTypeCharset != null ? httpEntityContentTypeCharset.toString() : null;
            long responseContentLength = httpEntity.getContentLength();

            Map<String, String> responseHeaders = new HashMap<>();
            for (Header header : httpResponse.getAllHeaders()) responseHeaders.put(header.getName(), header.getValue());

            T responseBean = (T) responseHandler.handleResponse(stringResponse);

            res.setSuccess(true);
            res.setErrorMessage(null);
            res.setErrorException(null);
            res.setHttpStatusCode(statusLine.getStatusCode());
            res.setHttpStatusDescription(statusLine.getReasonPhrase());
            res.setProtocolVersion(protocolVersion.toString());
            res.setContentRaw(stringResponse);
            res.setContentEncoding(responseCharset);
            res.setContentLength(responseContentLength);
            res.setContentType(responseContentType);
            res.setHeaders(responseHeaders);
            res.setResponseBean(responseBean);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);

            res.setSuccess(false);
            res.setErrorMessage("Exception executing request " + this + " - " + ex);
            res.setErrorException(ex);
        }

        return res;
    }

    private CloseableHttpClient _buildHttpClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();

        if (connectRequestTimeoutMs != null || connectTimeoutMs != null || socketTimeoutMs != null) {
            RequestConfig.Builder configBuilder = RequestConfig.custom();

            if (connectRequestTimeoutMs != null) {
                configBuilder.setConnectionRequestTimeout(connectRequestTimeoutMs);
            }

            if (connectTimeoutMs != null) {
                configBuilder.setConnectTimeout(connectTimeoutMs);
            }

            if (socketTimeoutMs != null) {
                configBuilder.setSocketTimeout(socketTimeoutMs);
            }
            builder.setDefaultRequestConfig(configBuilder.build());
        }

        if (proxy != null) {
            // set proxy from build pattern
            builder.setProxy(proxy);
        } else {
            // set proxy from system properties
            addProxyIfApplicable(builder);
        }
        builder.setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE);
        if (alternateSSLSocketFactory != null) {
            builder.setSSLSocketFactory(alternateSSLSocketFactory);
        }
        return builder.build();
    }


    // no worky worky
//    public WebClient andTrustAllSSL() {
//        try {
//            SSLContextBuilder builder = new SSLContextBuilder();
//            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
//            alternateSSLSocketFactory = new SSLConnectionSocketFactory(builder.build());
//        } catch (Exception e) {
//            log.error("Exception creating trust-all socket factory " + e, e);
//            throw new RuntimeException(e);
//        }
//        return this;
//    }


    public WebClient andTrustStore(KeyStore trustStore) {
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(trustStore);
            alternateSSLSocketFactory = new SSLConnectionSocketFactory(builder.build());
        } catch (Exception e) {
            log.error("Exception creating trust-all socket factory " + e, e);
            throw new RuntimeException(e);
        }
        return this;
    }

    public WebClient andTrustAllSSLThatYouShouldNeverUseInProduction() {
        try {
            SSLContextBuilder builder = new SSLContextBuilder();

            builder.loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    return true;
                }
            });
            alternateSSLSocketFactory = new SSLConnectionSocketFactory(builder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        } catch (Exception e) {
            log.error("Exception creating trust-all socket factory " + e, e);
            throw new RuntimeException(e);
        }
        return this;
    }

    private CloseableHttpClient getHttpClient() {
        if (alternateSSLSocketFactory != null) {
            return _buildHttpClient();
        }
//
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = _buildHttpClient();
                }
            }
        }
        return instance;

    }


    private <T> T performOptions() throws IOException {
        CloseableHttpClient httpclient = getHttpClient();

        HttpOptions httpOptions = new HttpOptions(url);
        log.debug("executing GET request " + httpOptions.getURI());

        for (Header header : headers) {
            log.debug("adding header " + header);
            httpOptions.addHeader(header);
        }

        if (directResponseHandler != null) {
            httpclient.execute(httpOptions, directResponseHandler);
            return null;
        } else {
            ResponseHandler<String> handler = new BasicResponseHandler();
            String responseBody = httpclient.execute(httpOptions, handler);
            log.debug("Response: (" + responseBody + ")");
            return (T) responseHandler.handleResponse(responseBody);
        }

    }

    private <T> T performGet() throws IOException {
        CloseableHttpClient httpclient = getHttpClient();

        HttpGet httpGet = new HttpGet(url);
        log.debug("executing GET request " + httpGet.getURI());

        for (Header header : headers) {
            log.debug("adding header " + header);
            httpGet.addHeader(header);
        }

        if (directResponseHandler != null) {
            T result = (T) httpclient.execute(httpGet, directResponseHandler);
            return result;
        } else {
            ResponseHandler<String> handler = new BasicResponseHandler();
            String responseBody = httpclient.execute(httpGet, handler);
            log.debug("Response: (" + responseBody + ")");
            return (T) responseHandler.handleResponse(responseBody);
        }
    }

    private <T> T performPost() throws IOException {
        CloseableHttpClient httpclient = getHttpClient();

        HttpPost httpPost = new HttpPost(url);

        log.debug("executing POST request " + httpPost.getURI());
        for (Header header : headers) {
            log.debug("adding header " + header);
            httpPost.addHeader(header);
        }
        if (binaryBody != null) {
            ByteArrayEntity be = new ByteArrayEntity(binaryBody);
            be.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, contentType));
            httpPost.setEntity(be);
        } else {
            StringEntity se = new StringEntity(body, charset);
            se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, contentType));
            httpPost.setEntity(se);
        }

        if (directResponseHandler != null) {
            T result = (T) httpclient.execute(httpPost, directResponseHandler);
            httpPost.releaseConnection();
            return result;
        } else {
            ResponseHandler<String> handler = new BasicResponseHandler();
            String responseBody = httpclient.execute(httpPost, handler);
            log.debug("Response: (" + responseBody + ")");
            httpPost.releaseConnection();
            return (T) responseHandler.handleResponse(responseBody);
        }
    }

    private <T> T performPut() throws IOException {
        CloseableHttpClient httpclient = getHttpClient();

        HttpPut httpPut = new HttpPut(url);

        log.debug("executing PUT request " + httpPut.getURI());
        for (Header header : headers) {
            log.debug("adding header " + header);
            httpPut.addHeader(header);
        }
        StringEntity se = new StringEntity(body, charset);
        se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, contentType));
        httpPut.setEntity(se);
        ResponseHandler<String> handler = new BasicResponseHandler();
        String responseBody = httpclient.execute(httpPut, handler);
        log.debug("Response: (" + responseBody + ")");
        return (T) responseHandler.handleResponse(responseBody);
    }

    private <T> T performDelete() throws IOException {
        CloseableHttpClient httpclient = getHttpClient();


        HttpDelete httpDelete = new HttpDelete(url);
        log.debug("executing DELETE request " + httpDelete.getURI());
        for (Header header : headers) {
            log.debug("adding header " + header);
            httpDelete.addHeader(header);
        }
        ResponseHandler<String> handler = new BasicResponseHandler();
        String responseBody = httpclient.execute(httpDelete, handler);
        log.debug("Response: (" + responseBody + ")");
        return (T) responseHandler.handleResponse(responseBody);
    }

    private void addProxyIfApplicable(HttpClientBuilder builder) {
        if (System.getProperty("outbound.http.proxy.ip") != null) {

            HttpHost _proxy = new HttpHost(
                    System.getProperty("outbound.http.proxy.ip"), // proxy ip
                    Integer.parseInt(System.getProperty("outbound.http.proxy.port")), // proxy port
                    System.getProperty("outbound.http.proxy.scheme")); // proxy scheme

            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(_proxy);
            builder.setRoutePlanner(routePlanner);
        }
    }

    @Override
    public String toString() {
        return "WebClient{" +
                "headers=" + headers +
                ", url='" + url + '\'' +
                ", responseHandler=" + responseHandler +
                ", body='" + body + '\'' +
                ", httpType='" + httpType + '\'' +
                ", charset='" + charset + '\'' +
                ", contentType='" + contentType + '\'' +
                ", connectTimeoutMs=" + connectTimeoutMs +
                ", connectRequestTimeoutMs=" + connectRequestTimeoutMs +
                ", socketTimeoutMs=" + socketTimeoutMs +
                ", alternateSSLSocketFactory=" + alternateSSLSocketFactory +
                ", proxy=" + proxy +
                ", directResponseHandler=" + directResponseHandler +
                '}';
    }
}