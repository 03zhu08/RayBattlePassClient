package cn.earthsky.raybattlepass.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return GSON.fromJson(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T fromPayload(String json, Class<T> clazz) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonElement payload = root.get("payload");
            if (payload != null && payload.isJsonObject()) {
                return GSON.fromJson(payload, clazz);
            }
            return GSON.fromJson(root, clazz);
        } catch (Exception e) {
            return null;
        }
    }
}
