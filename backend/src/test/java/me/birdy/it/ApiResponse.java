package me.birdy.it;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.List;

/** An HTTP answer of the API with its JSON body ({@code null} when the answer has no body). */
public record ApiResponse(int status, JsonValue body, String contentType) {

    public JsonObject object() {
        return body.asJsonObject();
    }

    public JsonArray array() {
        return body.asJsonArray();
    }

    public List<JsonObject> objects() {
        return array().getValuesAs(JsonObject.class);
    }

    public String error() {
        return object().getString("error");
    }

    public String message() {
        return object().getString("message");
    }

    public JsonObject fields() {
        return object().getJsonObject("fields");
    }

    public boolean hasBody() {
        return body != null;
    }
}
