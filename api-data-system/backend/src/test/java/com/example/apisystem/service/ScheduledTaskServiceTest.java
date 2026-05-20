package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.repository.ScheduledTaskRepository;
import com.example.apisystem.repository.TaskExecutionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledTaskService")
class ScheduledTaskServiceTest {

    @Mock
    private ScheduledTaskRepository scheduledTaskRepository;

    @Mock
    private TaskExecutionLogRepository taskExecutionLogRepository;

    private ScheduledTaskService scheduledTaskService;

    @BeforeEach
    void setUp() {
        scheduledTaskService = spy(new ScheduledTaskService(taskExecutionLogRepository));
        ReflectionTestUtils.setField(scheduledTaskService, "baseMapper", scheduledTaskRepository);
    }

    @Test
    @DisplayName("should save new task with default counts")
    void saveNewTask() {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("Test Task");
        task.setTaskCode("TEST_001");
        doReturn(true).when(scheduledTaskService).saveOrUpdate(any(ScheduledTask.class));

        boolean result = scheduledTaskService.saveTask(task);

        assertTrue(result);
        assertEquals(0, task.getExecuteCount());
        assertEquals(0, task.getSuccessCount());
        assertEquals(0, task.getFailCount());
        assertNotNull(task.getCreateTime());
        assertNotNull(task.getUpdateTime());
    }

    @Test
    @DisplayName("should toggle enabled task to disabled")
    void toggleEnableToDisable() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setEnableStatus("ENABLED");
        when(scheduledTaskRepository.selectById(1L)).thenReturn(task);
        doReturn(true).when(scheduledTaskService).updateById(any(ScheduledTask.class));

        boolean result = scheduledTaskService.toggleTaskStatus(1L);

        assertTrue(result);
        assertEquals("DISABLED", task.getEnableStatus());
    }

    @Test
    @DisplayName("should toggle disabled task to enabled")
    void toggleDisableToEnable() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setEnableStatus("DISABLED");
        when(scheduledTaskRepository.selectById(1L)).thenReturn(task);
        doReturn(true).when(scheduledTaskService).updateById(any(ScheduledTask.class));

        boolean result = scheduledTaskService.toggleTaskStatus(1L);

        assertTrue(result);
        assertEquals("ENABLED", task.getEnableStatus());
    }

    @Test
    @DisplayName("should return false when toggling non-existent task")
    void toggleNotFound() {
        when(scheduledTaskRepository.selectById(999L)).thenReturn(null);

        boolean result = scheduledTaskService.toggleTaskStatus(999L);

        assertFalse(result);
    }

    @Test
    @DisplayName("should update execution stats for success")
    void updateStatsSuccess() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setExecuteCount(5);
        task.setSuccessCount(3);
        task.setFailCount(2);
        task.setLastDuration(100L);
        when(scheduledTaskRepository.selectById(1L)).thenReturn(task);
        doReturn(true).when(scheduledTaskService).updateById(any(ScheduledTask.class));

        scheduledTaskService.updateTaskExecutionStats(1L, "SUCCESS", 200L, 100, 95, 5);

        assertEquals(6, task.getExecuteCount());
        assertEquals(4, task.getSuccessCount());
        assertEquals(2, task.getFailCount());
        assertEquals(200L, task.getLastDuration());
        assertNotNull(task.getLastExecuteTime());
    }

    @Test
    @DisplayName("should update execution stats for failure")
    void updateStatsFailure() {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setExecuteCount(5);
        task.setSuccessCount(3);
        task.setFailCount(2);
        when(scheduledTaskRepository.selectById(1L)).thenReturn(task);
        doReturn(true).when(scheduledTaskService).updateById(any(ScheduledTask.class));

        scheduledTaskService.updateTaskExecutionStats(1L, "FAILED", 500L, 0, 0, 0);

        assertEquals(6, task.getExecuteCount());
        assertEquals(3, task.getSuccessCount());
        assertEquals(3, task.getFailCount());
    }

    @Test
    @DisplayName("should find enabled tasks")
    void findEnabledTasks() {
        ScheduledTask task = new ScheduledTask();
        task.setEnableStatus("ENABLED");
        when(scheduledTaskRepository.selectList(any(QueryWrapper.class))).thenReturn(java.util.Arrays.asList(task));

        java.util.List<ScheduledTask> result = scheduledTaskService.findEnabledTasks();

        assertEquals(1, result.size());
        assertEquals("ENABLED", result.get(0).getEnableStatus());
    }
}
