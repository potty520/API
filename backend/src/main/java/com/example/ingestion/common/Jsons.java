package com.example.ingestion.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class Jsons {
    private final ObjectMapper mapper;

    public Jsons(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("JSON 序列化失败", error);
        }
    }

    public JsonNode tree(String value) {
        try {
            return mapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception error) {
            throw new ApiException(422, "JSON 配置格式不合法: " + error.getMessage());
        }
    }

    public Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception error) {
            throw new ApiException(422, "JSON 配置格式不合法: " + error.getMessage());
        }
    }
}
