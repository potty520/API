package com.example.apisystem.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CronExpressionParser")
class CronExpressionParserTest {

    private final CronExpressionParser parser = new CronExpressionParser();

    @Test
    @DisplayName("should return now + 5 minutes for next execution")
    void getNextExecution() {
        java.time.LocalDateTime from = java.time.LocalDateTime.of(2026, 5, 20, 10, 0, 0);
        java.time.LocalDateTime result = parser.getNextExecution(from);
        assertEquals(java.time.LocalDateTime.of(2026, 5, 20, 10, 5, 0), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0 */5 * * *",
        "0 0 * * *",
        "0 0 8 * *",
        "0 9 * * 1",
        "*/1 * * * *",
        "1,2,3 * * * *",
        "0 0 0 1 1"
    })
    @DisplayName("should validate correct 5-field cron expressions")
    void isValidTrue(String expr) {
        assertTrue(parser.isValid(expr), "Expected valid: " + expr);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "  ",
        "invalid",
        "0 */5 * * * *",  // 6 fields - invalid per this parser
        "* * * *",         // 4 fields
        "a b c d e"
    })
    @DisplayName("should reject invalid cron expressions")
    void isValidFalse(String expr) {
        assertFalse(parser.isValid(expr), "Expected invalid: " + expr);
    }

    @Test
    @DisplayName("should describe a valid cron expression")
    void describeValid() {
        String result = parser.describe("0 */5 * * *");
        assertTrue(result.contains("每"));
        assertTrue(result.contains("分"));
        assertTrue(result.contains("时"));
    }

    @Test
    @DisplayName("should return error description for invalid cron")
    void describeInvalid() {
        assertEquals("无效的 Cron 表达式", parser.describe("bad"));
    }

    @Test
    @DisplayName("should return error description for null")
    void describeNull() {
        assertEquals("无效的 Cron 表达式", parser.describe(null));
    }
}
