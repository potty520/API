package com.example.apisystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("scheduled_task")
public class ScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String taskCode;
    private Long apiConfigId;
    private Long fieldMappingId;
    private String targetTable;
    private String cronExpression;
    private String taskStatus;
    private String enableStatus;
    private LocalDateTime lastExecuteTime;
    private LocalDateTime nextExecuteTime;
    private Integer executeCount;
    private Integer successCount;
    private Integer failCount;
    private Long lastDuration;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }
    public Long getApiConfigId() { return apiConfigId; }
    public void setApiConfigId(Long apiConfigId) { this.apiConfigId = apiConfigId; }
    public Long getFieldMappingId() { return fieldMappingId; }
    public void setFieldMappingId(Long fieldMappingId) { this.fieldMappingId = fieldMappingId; }
    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getEnableStatus() { return enableStatus; }
    public void setEnableStatus(String enableStatus) { this.enableStatus = enableStatus; }
    public LocalDateTime getLastExecuteTime() { return lastExecuteTime; }
    public void setLastExecuteTime(LocalDateTime lastExecuteTime) { this.lastExecuteTime = lastExecuteTime; }
    public LocalDateTime getNextExecuteTime() { return nextExecuteTime; }
    public void setNextExecuteTime(LocalDateTime nextExecuteTime) { this.nextExecuteTime = nextExecuteTime; }
    public Integer getExecuteCount() { return executeCount; }
    public void setExecuteCount(Integer executeCount) { this.executeCount = executeCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public Long getLastDuration() { return lastDuration; }
    public void setLastDuration(Long lastDuration) { this.lastDuration = lastDuration; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
