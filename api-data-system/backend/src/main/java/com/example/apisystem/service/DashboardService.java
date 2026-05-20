package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.apisystem.dto.DashboardStatsDTO;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.repository.ScheduledTaskRepository;
import com.example.apisystem.repository.TaskExecutionLogRepository;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ScheduledTaskRepository scheduledTaskRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TaskExecutionLogService taskExecutionLogService;

    public DashboardService(ScheduledTaskRepository scheduledTaskRepository,
                           TaskExecutionLogRepository taskExecutionLogRepository,
                           TaskExecutionLogService taskExecutionLogService) {
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.taskExecutionLogRepository = taskExecutionLogRepository;
        this.taskExecutionLogService = taskExecutionLogService;
    }

    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();

        QueryWrapper<ScheduledTask> taskWrapper = new QueryWrapper<>();
        List<ScheduledTask> allTasks = scheduledTaskRepository.selectList(taskWrapper);
        stats.setTotalTasks(allTasks.size());

        QueryWrapper<ScheduledTask> runningWrapper = new QueryWrapper<>();
        runningWrapper.eq("enable_status", "ENABLED");
        stats.setRunningTasks(Math.toIntExact(scheduledTaskRepository.selectCount(runningWrapper)));

        Integer todaySuccess = taskExecutionLogService.countTodaySuccessExecutions();
        Integer todayFailed = taskExecutionLogService.countTodayFailedExecutions();
        stats.setTodaySuccessCount(todaySuccess != null ? todaySuccess : 0);
        stats.setTodayFailedCount(todayFailed != null ? todayFailed : 0);

        int total = stats.getTodaySuccessCount() + stats.getTodayFailedCount();
        stats.setSuccessRate(total > 0 ? (stats.getTodaySuccessCount() * 100 / total) : 100);

        Long todayDataCount = taskExecutionLogService.sumTodayNewInsertCount();
        stats.setTodayDataCount(todayDataCount != null ? todayDataCount : 0L);

        List<ScheduledTask> recentFailedTasks = scheduledTaskRepository.selectList(
            new QueryWrapper<ScheduledTask>()
                .eq("enable_status", "ENABLED")
                .last("ORDER BY update_time DESC LIMIT 5")
        );
        stats.setRecentFailedTasks(recentFailedTasks.stream().map(task -> {
            Map<String, Object> taskMap = new HashMap<>();
            taskMap.put("id", task.getId());
            taskMap.put("taskName", task.getTaskName());
            taskMap.put("taskCode", task.getTaskCode());
            taskMap.put("lastExecuteTime", task.getLastExecuteTime());
            taskMap.put("failCount", task.getFailCount());
            return taskMap;
        }).collect(Collectors.toList()));

        stats.setTaskStatusDistribution(getTaskStatusDistribution());

        List<TaskExecutionLog> recentExecutions = taskExecutionLogRepository.selectList(
            new QueryWrapper<TaskExecutionLog>()
                .last("ORDER BY create_time DESC LIMIT 10")
        );
        stats.setRecentExecutions(recentExecutions.stream().map(log -> {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("id", log.getId());
            logMap.put("taskName", log.getTaskName());
            logMap.put("batchId", log.getBatchId());
            logMap.put("executeStatus", log.getExecuteStatus());
            logMap.put("startTime", log.getStartTime());
            logMap.put("duration", log.getDuration());
            logMap.put("newInsertCount", log.getNewInsertCount());
            return logMap;
        }).collect(Collectors.toList()));

        return stats;
    }

    private List<Map<String, Object>> getTaskStatusDistribution() {
        List<Map<String, Object>> distribution = new ArrayList<>();

        QueryWrapper<ScheduledTask> enabledWrapper = new QueryWrapper<>();
        enabledWrapper.eq("enable_status", "ENABLED");
        long enabledCount = scheduledTaskRepository.selectCount(enabledWrapper);

        QueryWrapper<ScheduledTask> disabledWrapper = new QueryWrapper<>();
        disabledWrapper.eq("enable_status", "DISABLED");
        long disabledCount = scheduledTaskRepository.selectCount(disabledWrapper);

        Map<String, Object> enabled = new HashMap<>();
        enabled.put("name", "运行中");
        enabled.put("value", enabledCount);
        distribution.add(enabled);

        Map<String, Object> disabled = new HashMap<>();
        disabled.put("name", "已停止");
        disabled.put("value", disabledCount);
        distribution.add(disabled);

        return distribution;
    }
}
