package com.example.ingestion.common;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HashingTest {

    @Test
    void mapOrderDoesNotChangeHash() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "张三");
        first.put("age", 30);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("age", 30);
        second.put("name", "张三");
        assertEquals(Hashing.sha256(first), Hashing.sha256(second));
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertNotEquals(Hashing.sha256(Map.of("a", 1)), Hashing.sha256(Map.of("a", 2)));
        assertNotEquals(Hashing.sha256(Map.of("a", 1)), Hashing.sha256(Map.of("b", 1)));
    }

    @Test
    void stringHashMatchesKnownSha256Vector() {
        String hash = Hashing.sha256("abc");
        assertEquals(64, hash.length());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }
}
