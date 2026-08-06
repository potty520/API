package com.example.ingestion.scheduler;

import org.quartz.CronExpression;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class CronSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private CronSupport() {}

    public static String normalize(String input) {
        String text = input == null ? "" : input.trim().replaceAll("\\s+", " ");
        String[] fields = text.split(" ");
        if (fields.length == 5) {
            text = "0 " + text;
            fields = text.split(" ");
        }
        if (fields.length != 6 && fields.length != 7) throw new IllegalArgumentException("Cron 必须是 6 位或 7 位 Quartz 表达式");
        if (fields[3].equals("*") && fields[5].equals("*")) fields[5] = "?";
        return String.join(" ", fields);
    }

    public static List<LocalDateTime> nextRuns(String input, int count) {
        try {
            CronExpression expression = new CronExpression(normalize(input));
            expression.setTimeZone(java.util.TimeZone.getTimeZone(ZONE));
            Date cursor = new Date();
            List<LocalDateTime> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                cursor = expression.getNextValidTimeAfter(cursor);
                if (cursor == null) break;
                result.add(LocalDateTime.ofInstant(cursor.toInstant(), ZONE));
            }
            return result;
        } catch (ParseException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
    }

    public static boolean valid(String input) {
        try { return CronExpression.isValidExpression(normalize(input)); }
        catch (Exception ignored) { return false; }
    }
}
