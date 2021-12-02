package com.esquel.gateway.model;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class CallResults {
    private final List<String> results;

    public CallResults() {
        this.results = new ArrayList<>();
    }

    public void add(String jsonText) {
        results.add(jsonText);
    }

    public List<String> asList() {
        return results;
    }

    public Object asJSON() {
        if (results.size() == 1) {
            return JSONObject.parse(results.get(0));
        }
        return results.stream().map(JSON::parseObject).collect(toList());
    }
}
