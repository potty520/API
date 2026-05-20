package com.example.apisystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("task_execution_log")
public class TaskExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String taskName;
    private String batchId;
    private String executeStatus;
    private String startTime;
    private String endTime;
    private Long duration;
    private Integer totalRecords;
    private Integer newInsertCount;
    private Integer existingCount;
    private String requestBody;
    private String responseBody;
    private String errorMessage;
    private String stackTrace;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getExecuteStatus() { return executeStatus; }
    public void setExecuteStatus(String executeStatus) { this.executeStatus = executeStatus; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
    public Integer getNewInsertCount() { return newInsertCount; }
    public void setNewInsertCount(Integer newInsertCount) { this.newInsertCount = newInsertCount; }
    public Integer getExistingCount() { return existingCount; }
    public void setExistingCount(Integer existingCount) { this.existingCount = existingCount; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
