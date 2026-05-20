package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.repository.TaskExecutionLogRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskExecutionLogService extends ServiceImpl<TaskExecutionLogRepository, TaskExecutionLog> {

    public Page<TaskExecutionLog> findPage(Long taskId, int pageNum, int pageSize) {
        Page<TaskExecutionLog> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        wrapper.orderByDesc("create_time");
        return this.page(page, wrapper);
    }

    public TaskExecutionLog findById(Long id) {
        return this.getById(id);
    }

    public TaskExecutionLog createLog(Long taskId, String taskName) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setTaskId(taskId);
        log.setTaskName(taskName);
        log.setBatchId(generateBatchId());
        log.setExecuteStatus("RUNNING");
        log.setStartTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.setCreateTime(LocalDateTime.now());
        this.save(log);
        return log;
    }

    public void updateLogSuccess(TaskExecutionLog log, long duration, int totalRecords, int newInsertCount, int existingCount) {
        log.setExecuteStatus("SUCCESS");
        log.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.setDuration(duration);
        log.setTotalRecords(totalRecords);
        log.setNewInsertCount(newInsertCount);
        log.setExistingCount(existingCount);
        this.updateById(log);
    }

    public void updateLogFailed(TaskExecutionLog log, long duration, String errorMessage, String stackTrace) {
        log.setExecuteStatus("FAILED");
        log.setEndTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.setDuration(duration);
        log.setErrorMessage(errorMessage);
        log.setStackTrace(stackTrace);
        this.updateById(log);
    }

    public List<TaskExecutionLog> findRecentLogs(int limit) {
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        wrapper.last("LIMIT " + limit);
        return this.list(wrapper);
    }

    public List<TaskExecutionLog> findRecentFailedLogs(int limit) {
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("execute_status", "FAILED");
        wrapper.orderByDesc("create_time");
        wrapper.last("LIMIT " + limit);
        return this.list(wrapper);
    }

    private String generateBatchId() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "TASK_" + date + "_" + uuid.toUpperCase();
    }

    public Integer countTodaySuccessExecutions() {
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("execute_status", "SUCCESS");
        wrapper.apply("DATE(create_time) = CURDATE()");
        return Math.toIntExact(this.count(wrapper));
    }

    public Integer countTodayFailedExecutions() {
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.eq("execute_status", "FAILED");
        wrapper.apply("DATE(create_time) = CURDATE()");
        return Math.toIntExact(this.count(wrapper));
    }

    public Long sumTodayNewInsertCount() {
        QueryWrapper<TaskExecutionLog> wrapper = new QueryWrapper<>();
        wrapper.apply("DATE(create_time) = CURDATE()");
        wrapper.select("SUM(new_insert_count) as newInsertCount");
        TaskExecutionLog result = this.getOne(wrapper);
        return result != null && result.getNewInsertCount() != null ? Long.valueOf(result.getNewInsertCount()) : 0L;
    }
}
