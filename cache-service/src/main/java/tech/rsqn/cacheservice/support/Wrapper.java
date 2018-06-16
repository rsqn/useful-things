package tech.rsqn.cacheservice.support;

import java.io.Serializable;


public class Wrapper implements Serializable {

    public static final long serialVersionUid = -13478294782948952L;
    private String className;
    private String jsonBody;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getJsonBody() {
        return jsonBody;
    }

    public void setJsonBody(String jsonBody) {
        this.jsonBody = jsonBody;
    }
}
