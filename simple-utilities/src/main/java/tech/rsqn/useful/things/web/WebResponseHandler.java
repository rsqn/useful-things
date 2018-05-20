package tech.rsqn.useful.things.web;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 2/16/13
 * Time: 10:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface WebResponseHandler<T> {

    T handleResponse(String responseBody);
}
