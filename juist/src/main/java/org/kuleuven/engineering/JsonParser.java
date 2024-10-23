package org.kuleuven.engineering;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonParser {

    public static JSONObject parseString(String json) {
        return new JSONObject(json);
    }

    public static JSONArray parseArray(String json) {
        return new JSONArray(json);
    }

    public static List<Map<String, Object>> toList(JSONArray array) {
        return array.toList().stream()
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toList());
    }
}
