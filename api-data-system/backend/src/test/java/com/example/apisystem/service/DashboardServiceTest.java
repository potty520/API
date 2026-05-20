package com.example.apisystem.service;

import com.example.apisystem.dto.DashboardStatsDTO;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.repository.ScheduledTaskRepository;
import com.example.apisystem.repository.TaskExecutionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    @Mock
    private ScheduledTaskRepository scheduledTaskRepository;

    @Mock
    private TaskExecutionLogRepository taskExecutionLogRepository;

    @Mock
    private TaskExecutionLogService taskExecutionLogService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(scheduledTaskRepository, taskExecutionLogRepository, taskExecutionLogService);
    }

    @Test
    @DisplayName("should compute dashboard stats with zero data")
    void getStatsEmpty() {
        when(scheduledTaskRepository.selectList(any())).thenReturn(new ArrayList<>());
        when(scheduledTaskRepository.selectCount(any())).thenReturn(0L);
        when(taskExecutionLogService.countTodaySuccessExecutions()).thenReturn(0);
        when(taskExecutionLogService.countTodayFailedExecutions()).thenReturn(0);
        when(taskExecutionLogService.sumTodayNewInsertCount()).thenReturn(0L);

        DashboardStatsDTO stats = dashboardService.getDashboardStats();

        assertEquals(0, stats.getTotalTasks());
        assertEquals(0, stats.getRunningTasks());
        assertEquals(100, stats.getSuccessRate()); // default 100% when no data
        assertEquals(0L, stats.getTodayDataCount());
        assertEquals(0, stats.getTodaySuccessCount());
        assertEquals(0, stats.getTodayFailedCount());
        assertNotNull(stats.getTaskStatusDistribution());
        assertNotNull(stats.getRecentExecutions());
        assertNotNull(stats.getRecentFailedTasks());
    }

    @Test
    @DisplayName("should compute success rate correctly")
    void getStatsSuccessRate() {
        when(scheduledTaskRepository.selectList(any())).thenReturn(new ArrayList<>());
        when(scheduledTaskRepository.selectCount(any())).thenReturn(5L);
        when(taskExecutionLogService.countTodaySuccessExecutions()).thenReturn(8);
        when(taskExecutionLogService.countTodayFailedExecutions()).thenReturn(2);
        when(taskExecutionLogService.sumTodayNewInsertCount()).thenReturn(500L);

        DashboardStatsDTO stats = dashboardService.getDashboardStats();

        assertEquals(5, stats.getRunningTasks());
        assertEquals(80, stats.getSuccessRate()); // 8/10 = 80%
        assertEquals(500L, stats.getTodayDataCount());
        assertEquals(8, stats.getTodaySuccessCount());
        assertEquals(2, stats.getTodayFailedCount());
    }
}
