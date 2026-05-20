package com.example.apisystem.dto;

public class TaskScheduleDTO {
    private Long id;
    private String taskName;
    private String taskCode;
    private Long apiConfigId;
    private String apiName;
    private String cronExpression;
    private String taskStatus;
    private String enableStatus;
    private String lastExecuteTime;
    private String nextExecuteTime;
    private Integer executeCount;
    private Integer successCount;
    private Integer failCount;
    private Long lastDuration;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public Long getApiConfigId() { return apiConfigId; }
    public void setApiConfigId(Long apiConfigId) { this.apiConfigId = apiConfigId; }
    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getEnableStatus() { return enableStatus; }
    public void setEnableStatus(String enableStatus) { this.enableStatus = enableStatus; }
    public String getLastExecuteTime() { return lastExecuteTime; }
    public void setLastExecuteTime(String lastExecuteTime) { this.lastExecuteTime = lastExecuteTime; }
    public String getNextExecuteTime() { return nextExecuteTime; }
    public void setNextExecuteTime(String nextExecuteTime) { this.nextExecuteTime = nextExecuteTime; }
    public Integer getExecuteCount() { return executeCount; }
    public void setExecuteCount(Integer executeCount) { this.executeCount = executeCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public Long getLastDuration() { return lastDuration; }
    public void setLastDuration(Long lastDuration) { this.lastDuration = lastDuration; }
}
