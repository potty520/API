package com.example.apisystem.dto;

import java.util.List;
import java.util.Map;

public class DashboardStatsDTO {
    private Integer totalTasks;
    private Integer runningTasks;
    private Integer successRate;
    private Long todayDataCount;
    private Integer todaySuccessCount;
    private Integer todayFailedCount;
    private List<Map<String, Object>> recentFailedTasks;
    private List<Map<String, Object>> taskStatusDistribution;
    private List<Map<String, Object>> recentExecutions;

    public Integer getTotalTasks() { return totalTasks; }
    public void setTotalTasks(Integer totalTasks) { this.totalTasks = totalTasks; }
    public Integer getRunningTasks() { return runningTasks; }
    public void setRunningTasks(Integer runningTasks) { this.runningTasks = runningTasks; }
    public Integer getSuccessRate() { return successRate; }
    public void setSuccessRate(Integer successRate) { this.successRate = successRate; }
    public Long getTodayDataCount() { return todayDataCount; }
    public void setTodayDataCount(Long todayDataCount) { this.todayDataCount = todayDataCount; }
    public Integer getTodaySuccessCount() { return todaySuccessCount; }
    public void setTodaySuccessCount(Integer todaySuccessCount) { this.todaySuccessCount = todaySuccessCount; }
    public Integer getTodayFailedCount() { return todayFailedCount; }
    public void setTodayFailedCount(Integer todayFailedCount) { this.todayFailedCount = todayFailedCount; }
    public List<Map<String, Object>> getRecentFailedTasks() { return recentFailedTasks; }
    public void setRecentFailedTasks(List<Map<String, Object>> recentFailedTasks) { this.recentFailedTasks = recentFailedTasks; }
    public List<Map<String, Object>> getTaskStatusDistribution() { return taskStatusDistribution; }
    public void setTaskStatusDistribution(List<Map<String, Object>> taskStatusDistribution) { this.taskStatusDistribution = taskStatusDistribution; }
    public List<Map<String, Object>> getRecentExecutions() { return recentExecutions; }
    public void setRecentExecutions(List<Map<String, Object>> recentExecutions) { this.recentExecutions = recentExecutions; }
}
