package com.example.apisystem.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Test
    @DisplayName("should create success response with data")
    void successWithData() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertEquals(200, response.getCode());
        assertEquals("Success", response.getMessage());
        assertEquals("hello", response.getData());
    }

    @Test
    @DisplayName("should create success response with custom message and data")
    void successWithMessage() {
        ApiResponse<Integer> response = ApiResponse.success("OK", 42);

        assertEquals(200, response.getCode());
        assertEquals("OK", response.getMessage());
        assertEquals(42, response.getData());
    }

    @Test
    @DisplayName("should create error response")
    void error() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");

        assertEquals(500, response.getCode());
        assertEquals("Something went wrong", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("should create error response with custom code")
    void errorWithCode() {
        ApiResponse<Void> response = ApiResponse.error(400, "Bad Request");

        assertEquals(400, response.getCode());
        assertEquals("Bad Request", response.getMessage());
        assertNull(response.getData());
    }
}
