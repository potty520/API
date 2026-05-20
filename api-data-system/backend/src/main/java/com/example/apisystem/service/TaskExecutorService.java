package com.example.apisystem.service;

import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TaskExecutorService {

    private final ScheduledTaskService scheduledTaskService;
    private final TaskExecutionLogService taskExecutionLogService;

    public TaskExecutorService(ScheduledTaskService scheduledTaskService,
                               TaskExecutionLogService taskExecutionLogService) {
        this.scheduledTaskService = scheduledTaskService;
        this.taskExecutionLogService = taskExecutionLogService;
    }

    public void executeTask(Long taskId) {
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }

        TaskExecutionLog log = taskExecutionLogService.createLog(taskId, task.getTaskName());
        long startTime = System.currentTimeMillis();

        try {
            Thread.sleep(1000);

            int totalRecords = 100;
            int newInsertCount = 95;
            int existingCount = 5;
            long duration = System.currentTimeMillis() - startTime;

            taskExecutionLogService.updateLogSuccess(log, duration, totalRecords, newInsertCount, existingCount);
            scheduledTaskService.updateTaskExecutionStats(taskId, "SUCCESS", duration, totalRecords, newInsertCount, existingCount);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMessage = e.getMessage();
            String stackTrace = getStackTraceAsString(e);

            taskExecutionLogService.updateLogFailed(log, duration, errorMessage, stackTrace);
            scheduledTaskService.updateTaskExecutionStats(taskId, "FAILED", duration, 0, 0, 0);
        }
    }

    public String getNextExecuteTime(String cronExpression) {
        try {
            CronExpressionParser parser = new CronExpressionParser(cronExpression);
            LocalDateTime nextTime = parser.getNextExecution();
            return nextTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return "Invalid Cron Expression";
        }
    }

    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
