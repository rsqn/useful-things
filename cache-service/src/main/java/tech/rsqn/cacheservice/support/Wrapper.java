package tech.rsqn.cacheservice.support;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 4/20/13
 *
 * To change this template use File | Settings | File Templates.
 */
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
