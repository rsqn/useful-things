package tech.rsqn.useful.things.web;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 18/08/13
 * Time: 8:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class TextPlainResponseHandler implements WebResponseHandler<String> {

    @Override
    public String handleResponse(String responseBody) {
        return responseBody;
    }
}
