package tech.rsqn.useful.things.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Created with IntelliJ IDEA.
 * User: mandrewes
 * Date: 2/16/13
 * Time: 10:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class JsonObjectResponseHandler implements WebResponseHandler<JsonObject> {

    @Override
    public JsonObject handleResponse(String responseBody) {
        JsonParser parser = new JsonParser();
        JsonElement ret = parser.parse(responseBody);
        return ret.getAsJsonObject();
    }
}
