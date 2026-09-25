package com.example.ingestion.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CronSupportTest {

    @Test
    void expandsFiveFieldCrontabToQuartzFormat() {
        assertEquals("0 */5 * * * ?", CronSupport.normalize("*/5 * * * *"));
        assertEquals("0 0 2 * * MON", CronSupport.normalize("0 2 * * MON"));
    }

    @Test
    void collapsesWhitespaceAndKeepsSixFieldExpression() {
        assertEquals("0 0 3 * * ?", CronSupport.normalize("  0   3  *  *  * "));
        assertEquals("0 0 3 * * ?", CronSupport.normalize("0 0 3 * * ?"));
    }

    @Test
    void rejectsWrongFieldCount() {
        assertThrows(IllegalArgumentException.class, () -> CronSupport.normalize("* * *"));
        assertThrows(IllegalArgumentException.class, () -> CronSupport.normalize(null));
    }

    @Test
    void validatesExpressions() {
        assertTrue(CronSupport.valid("0 0/10 * * * ?"));
        assertTrue(CronSupport.valid("*/10 * * * *"));
        assertFalse(CronSupport.valid("不是 cron 表达式"));
    }

    @Test
    void computesIncreasingNextRunTimes() {
        List<LocalDateTime> runs = CronSupport.nextRuns("0 0/5 * * * ?", 3);
        assertEquals(3, runs.size());
        assertTrue(runs.get(0).isBefore(runs.get(1)));
        assertTrue(runs.get(1).isBefore(runs.get(2)));
        assertEquals(0, runs.get(0).getMinute() % 5);
        assertEquals(0, runs.get(0).getSecond());
    }
}
