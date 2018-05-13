package tech.rsqn.useful.things.web;

import java.util.Map;

public class WebClientResponse<T>
{
    private int httpStatusCode;
    private String httpStatusDescription;
    private String contentType;
    private long contentLength;
    private String contentEncoding;
    private String contentRaw;
    private Map<String, String> headers;
    private boolean success;
    private String errorMessage;
    private Exception errorException;
    private String protocolVersion;
    private T responseBean;

    public int getHttpStatusCode()
    {
        return httpStatusCode;
    }

    public void setHttpStatusCode(int httpStatusCode)
    {
        this.httpStatusCode = httpStatusCode;
    }

    public String getHttpStatusDescription()
    {
        return httpStatusDescription;
    }

    public void setHttpStatusDescription(String httpStatusDescription)
    {
        this.httpStatusDescription = httpStatusDescription;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public long getContentLength()
    {
        return contentLength;
    }

    public void setContentLength(long contentLength)
    {
        this.contentLength = contentLength;
    }

    public String getContentEncoding()
    {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding)
    {
        this.contentEncoding = contentEncoding;
    }

    public String getContentRaw()
    {
        return contentRaw;
    }

    public void setContentRaw(String contentRaw)
    {
        this.contentRaw = contentRaw;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, String> headers)
    {
        this.headers = headers;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public Exception getErrorException()
    {
        return errorException;
    }

    public void setErrorException(Exception errorException)
    {
        this.errorException = errorException;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion)
    {
        this.protocolVersion = protocolVersion;
    }

    public T getResponseBean()
    {
        return responseBean;
    }

    public void setResponseBean(T responseBean)
    {
        this.responseBean = responseBean;
    }
}
