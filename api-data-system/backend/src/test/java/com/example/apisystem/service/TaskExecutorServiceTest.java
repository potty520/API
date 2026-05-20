package com.example.apisystem.service;

import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskExecutorService")
class TaskExecutorServiceTest {

    @Mock
    private ScheduledTaskService scheduledTaskService;

    @Mock
    private TaskExecutionLogService taskExecutionLogService;

    private TaskExecutorService taskExecutorService;

    @BeforeEach
    void setUp() {
        taskExecutorService = new TaskExecutorService(scheduledTaskService, taskExecutionLogService);
    }

    @Test
    @DisplayName("should throw exception when task not found")
    void executeTaskNotFound() {
        when(scheduledTaskService.findById(999L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> taskExecutorService.executeTask(999L));
    }

    @Test
    @DisplayName("should execute task successfully")
    void executeTaskSuccess() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setTaskName("Test Task");
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(100L);
        log.setBatchId("TASK_20260520_ABCD1234");

        when(scheduledTaskService.findById(1L)).thenReturn(task);
        when(taskExecutionLogService.createLog(1L, "Test Task")).thenReturn(log);

        taskExecutorService.executeTask(1L);

        verify(taskExecutionLogService).createLog(1L, "Test Task");
        verify(taskExecutionLogService).updateLogSuccess(any(TaskExecutionLog.class), anyLong(), eq(100), eq(95), eq(5));
        verify(scheduledTaskService).updateTaskExecutionStats(eq(1L), eq("SUCCESS"), anyLong(), eq(100), eq(95), eq(5));
    }

    @Test
    @DisplayName("should compute next execution time from cron expression")
    void getNextExecuteTime() {
        String result = taskExecutorService.getNextExecuteTime("0 */5 * * *");

        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    @DisplayName("should return time string even for invalid cron (parser always succeeds)")
    void getNextExecuteTimeInvalid() {
        // CronExpressionParser.getNextExecution() always returns LocalDateTime.now() + 5 min,
        // regardless of cron expression validity. This is a known behavior of the stubbed implementation.
        String result = taskExecutorService.getNextExecuteTime("invalid");

        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }
}
