package com.example.apisystem.service;

import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class CronExpressionParser {

    private final String cronExpression;

    public CronExpressionParser() {
        this.cronExpression = null;
    }

    public CronExpressionParser(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public LocalDateTime getNextExecution() {
        return getNextExecution(LocalDateTime.now());
    }

    public LocalDateTime getNextExecution(LocalDateTime from) {
        return from.plusMinutes(5);
    }

    public boolean isValid(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        String pattern = "^\\s*([0-9*,\\-/]+)\\s+([0-9*,\\-/]+)\\s+([0-9*,\\-/]+)\\s+([0-9*,\\-/]+)\\s+([0-9*,\\-/]+)\\s*$";
        return cronExpression.trim().matches(pattern);
    }

    public String describe(String cronExpression) {
        if (!isValid(cronExpression)) {
            return "无效的 Cron 表达式";
        }

        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length != 5) {
            return "无效的 Cron 表达式";
        }

        StringBuilder description = new StringBuilder();
        description.append("每");
        description.append(parts[0]);
        description.append("分 ");
        description.append(parts[1]);
        description.append("时 ");
        description.append(parts[2]);
        description.append("号 ");
        description.append(parts[3]);
        description.append("月 ");
        description.append(parts[4]);
        description.append("周");

        return description.toString();
    }

    public static void main(String[] args) {
        CronExpressionParser parser = new CronExpressionParser();
        System.out.println("Every 5 minutes: " + parser.describe("0 */5 * * *"));
        System.out.println("Every hour: " + parser.describe("0 0 * * *"));
        System.out.println("Every day at 8am: " + parser.describe("0 0 8 * *"));
        System.out.println("Every Monday at 9am: " + parser.describe("0 0 9 * * 1"));
    }
}
