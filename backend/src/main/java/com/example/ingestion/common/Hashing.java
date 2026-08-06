package com.example.ingestion.common;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Hashing {
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private Hashing() {}

    public static String sha256(Object value) {
        try {
            byte[] bytes = value instanceof String text
                    ? text.getBytes(StandardCharsets.UTF_8)
                    : CANONICAL.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("哈希计算失败", error);
        }
    }
}
